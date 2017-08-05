(ns cider-ci.server.commits.ui
  (:refer-clojure :exclude [str keyword])
  (:require-macros
    [reagent.ratom :as ratom :refer [reaction]]
    [cljs.core.async.macros :refer [go]]
    )
  (:require
    [cider-ci.server.ui2.shared :refer [pre-component]]
    [cider-ci.utils.core :refer [keyword str presence]]
    [cider-ci.server.ui2.constants :refer [CONTEXT]]
    [cider-ci.server.client.state :as state]
    [cider-ci.server.client.routes :as routes]

    [cider-ci.server.repository.ui.projects.shared :refer [humanize-datetime]]
    [cider-ci.utils.sha1]

    [clojure.contrib.inflect :refer [pluralize-noun]]

    [goog.string :as gstring]
    [goog.string.format]

    [reagent.core :as reagent]
    [secretary.core :as secretary :include-macros true]
    [cljs-http.client :as http]))

(defn fetch-commits []
  (let [id (.random js/Math)
        request {:url "/cider-ci/repositories/tree-commits/"
                 :accept :json
                 :id id}]
    (swap! state/client-state
           assoc-in [:commits-page :fetch-request :request] request)
    (go (let [resp (<! (http/request request))]
          (when (= id (-> @state/client-state :commits-page :fetch-request :request :id)
                   (swap! state/client-state
                          assoc-in [:commits-page :fetch-request :response] resp)))))))

(defn gravatar-url [email]
  (->> email
       clojure.string/trim
       clojure.string/lower-case
       cider-ci.utils.sha1/md5-hex
       (gstring/format "https://www.gravatar.com/avatar/%s?s=20&d=retro")))

(declare page)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn update-form-data [fun]
  (swap! state/client-state
         (fn [cs]
           (assoc-in cs [:commits-page :form-data]
                     (fun (-> cs :commits-page :form-data))))))

(defn update-form-data-value [k v]
  (update-form-data (fn [fd] (assoc fd k v))))


(def form-data (reaction (-> @state/client-state :commits-page :form-data)))


(defn form-component []
  [:div.form
   [:div.form-group
    [:label "Project Name" ]
    [:input#project_name.form-control
     {:placeholder "My Project"
      :on-change #(update-form-data-value :project_name (-> % .-target .-value presence))
      :value (-> @form-data :project_name)
      }]]
   [:div.form-group
    [:label "Branch Name" ]
    [:input#branch_name.form-control
     {:placeholder "My Branch"
      :on-change #(update-form-data-value :branch_name (-> % .-target .-value presence))
      :value (-> @form-data :branch_name)
      }]]
   [:div.form-group.row
    [:div.col-xs-6 [:a.btn.btn-warning {:href (routes/commits-path)} [:i.fa.fa-remove] " Reset "]]
    [:div.col-xs-6 [:a.pull-right.btn.btn-primary
                    {:href (routes/commits-path {:query-params @form-data})}
                    [:i.fa.fa-filter]  " Filter "]]]])


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def tree-commits (reaction (-> @state/client-state :commits-page :fetch-request :response :body)))

(defn project-link [project]
  [:a {:href (str "/cider-ci/repositories/projects/" (:id project))}
   [:b (:name project)] " "])

(defn project-filter [project]
  [:a {:href "#"
       :on-click (fn []
                   (update-form-data-value :project_name (:name project))
                   (update-form-data-value :branch_name nil))
       } [:i.fa.fa-filter] " "])

(defn project-remote-commit-link [commit project]
  (when-let [http-url (re-matches #"http.*" (:git_url project))]
    [:a {:key http-url
         :href (str (clojure.string/replace http-url #"\.git$" "")
                    "/commit/" (:id commit))}
     (case (:type project)
       "github" [:i.fa.fa-github]
       "gitlab" [:i.fa.fa-gitlab]
       [:i.fa.fa-git])]))

(defn branch-comp [dist-from-head branch]
  [:span.branch {:style
                 {:font-weight (if (= 0 dist-from-head)
                                 "strong"
                                 "normal")}}
   (when-let [repository-name (:repository_name_placeholder branch)]
     [:span.repository
      [:span.repository-name repository-name]
      [:span "/"]])
   [:span (:name branch)]
   ""
   [:a.author.committer
    {:href "#"
     :title (case dist-from-head
              0 (str "This commit is the head of the branch "
                     (:name branch) ".")
              (str "This commit is " dist-from-head " "
                   (pluralize-noun dist-from-head "commit")
                   " behind the head of the branch "
                   (:name branch) "."))
     :data-toggle "tooltip" }
    [:sub.badge {:style
                 {:padding "3px 5px"
                  :margin "1px 1px"
                  :font-size "8px"}
                 } dist-from-head]]])

(defn branch-filter [project branch]
  [:a {:href (routes/commits-path {:query-params (merge (-> @state/page-state :current-page :query-params)
                                                 {:project_name (:name project)
                                                  :branch_name (:name branch)})})}
   [:i.fa.fa-filter] " "])

       ;:on-click (fn []
                   ;(update-form-data-value :project_name (:name project))
                   ;(update-form-data-value :branch_name (:name branch))
                   ;(let [path (commits-path {:query-params @form-data})] (js/console.log path) ; this causes stuff internally but it doesn't set the url :-( (secretary/dispatch! path))
       ;            )

(defn project-branches [project]
  [:span
   (doall (for [branch (doall (:branches project))]
            (let [headdist (:distance_to_head branch)]
              [:span {:key (str (:id project) "/" (:name branch))
                    :style {:font-weight (if (= 0 headdist)
                                           "strong" "normal")}}
               (if (= 0 headdist)
                 [:b (branch-comp headdist branch)]
                 [:span (branch-comp headdist branch)]) " "
               (branch-filter project branch)
               ])))])

(defn projects [commit]
  [:ul.list-inline
   (doall (for [project (doall (:projects commit))]
     [:li {:key (:id project)}
      (project-link project)
      (project-filter project)
      (project-remote-commit-link commit project)
      " : "
      (project-branches project)]))])

(defn commit-id [commit]
  [:span (str "\u2009" (->> commit :id (take 6) clojure.string/join) "\u2009")])

(defn author-committer [commit]
  (if (= (-> commit :committer_name)
         (-> commit :author_name))
    [:a.author.committer
     {:href "#"
      :title (str " Authored and committed by "
                  (-> commit :author_name) " "
                  (humanize-datetime (:timestamp @state/client-state)
                                     (-> commit :committer_date)))
      :data-toggle "tooltip" :data-html "true"}
     [:img {:src (-> commit :committer_email gravatar-url)}]]
    [:span
     [:a.author
      {:href "#"
       :title (str " Authored by "
                   (-> commit :author_name) " "
                   (humanize-datetime (:timestamp @state/client-state)
                                      (-> commit :author_date)))
       :data-toggle "tooltip" :data-html "true"}
      [:img {:src (-> commit :author_email gravatar-url)}]]
     " / "
     [:a.committer
      {:href "#"
       :title (str " Committed by "
                   (-> commit :committer_name) " "
                   (humanize-datetime (:timestamp @state/client-state)
                                      (-> commit :committer_date)))
       :data-toggle "tooltip" :data-html "true"}
      [:img {:src (-> commit :committer_email gravatar-url)}]]]))

(defn commited-at [commit]
  (humanize-datetime (:timestamp @state/client-state)
                     (-> commit :committer_date)))

(defn tree-commit-component [tree-commit]
  [:span
   [:a.tree-id
    {:href (routes/tree-path {:tree-id (:tree_id tree-commit)})}
    [:i.fa.fa-tree.text-muted]
    [:span " "]
    [:span.git-ref.tree-id (->> tree-commit :tree_id (take 6) clojure.string/join)]]
   [:span.commits
    [:ul.commits.list-inline
     (doall (for [commit (doall (:commits tree-commit))]
              [:li.commit {:key (str :tree_id " " :id commit)}
               [:ul.list-inline
                [:li.commit-id (commit-id commit)]
                [:li.commited-at (commited-at commit)]
                [:li.author-committer (author-committer commit)]
                [:li [:em (:subject commit)]]
                [:li.projects (projects commit)]
                ]]))]]])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn post-mount-setup [component]
  (fetch-commits)
  (.tooltip (js/$ (reagent.core/dom-node component))))

(defn page []
  ; TODO Filter ähnlich Workspace page
  ; API liefert commits mit tree-id usw; jobs werden in dedizierten requests geholt
  ; Notifications gemäss Filer (wie soll das funktionierten?)
  ; Auch Tree-id notifications
  (reagent/create-class
    {
     ;:component-will-mount fetch-commits
     :component-did-mount post-mount-setup
     :reagent-render
     (fn []
       [:div.commits-page
        ;[:pre (-> @state/page-state :current-page :query-params str)]
        [:div.alert.alert-warning
         [:p "This page shows a " [:b "not yet functional prototype!"]]
         [:p [:span "You are probably looking for the  "]
          [:b [:a {:href "/cider-ci/ui/workspace" } "Workspace" ]]
          [:span " page."]]]
        [:h1 "Commits"]
        [form-component]
        [:ul.tree-commits.list-unstyled
         (doall (for [tree-commit @tree-commits]
                  [:li {:key (:tree_id tree-commit)}(tree-commit-component tree-commit)]))]

        (when (:debug @state/client-state)
          [:div.page-debug
           [:hr.clearfix]
           [:h2 "Page Debug"]
           [:h4 "Tree-Commits"]
           (pre-component @tree-commits)
           ])

        ])}))
