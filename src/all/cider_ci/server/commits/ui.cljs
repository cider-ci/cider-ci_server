(ns cider-ci.server.commits.ui
  (:refer-clojure :exclude [str keyword])
  (:require-macros
    [reagent.ratom :as ratom :refer [reaction]]
    [cljs.core.async.macros :refer [go]]
    )
  (:require
    [cider-ci.server.client.request :as request]
    [cider-ci.server.client.routes :as routes]
    [cider-ci.server.client.state :as state]
    [cider-ci.server.ui2.constants :refer [CONTEXT]]
    [cider-ci.server.ui2.shared :refer [pre-component]]
    [cider-ci.utils.core :refer [keyword str presence]]
    [cider-ci.server.repository.ui.projects.shared :refer [humanize-datetime]]

    [accountant.core :as accountant]
    [cider-ci.utils.sha1]
    [cljs-http.client :as http]
    [cljs.core.async :as async]
    [cljs.pprint :refer [pprint]]
    [clojure.contrib.inflect :refer [pluralize-noun]]
    [goog.string :as gstring]
    [goog.string.format]
    [reagent.core :as reagent]
    [reagent.ratom :as ratom]
    [secretary.core :as secretary :include-macros true]
    ))

(declare page fetch-commits)

(def form-data* (reaction (-> @state/client-state :commits-page :form-data)))

(defn gravatar-url [email]
  (->> email
       clojure.string/trim
       clojure.string/lower-case
       cider-ci.utils.sha1/md5-hex
       (gstring/format "https://www.gravatar.com/avatar/%s?s=20&d=retro")))

;;; current query parameters ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
; this is used to listen to actual updates of the query params and to fetch
; the commits accordingly

(def current-query-paramerters*
  (reaction (-> @state/page-state :current-page :query-params)))

(defn current-query-params-component []
  (reagent/create-class
    {:component-did-mount #(fetch-commits)
     :component-did-update #(fetch-commits)
     :reagent-render
     (fn [_] [:div.current-query-parameters
              {:style {:display :none}}
              [:h3 "Current-Query-Parameters"]
              [pre-component @current-query-paramerters*]])}))


;;; project-and-branch-names ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def project-and-branch-names* (ratom/atom {}))

; TODO implement reloading on proper conditions
(defn fetch-project-and-branchnames []
  (let [url (str "/cider-ci/commits/project-and-branchnames/" )
        resp-chan (async/chan)]
    (request/send-off {:url url :method :get}
                      {:modal true
                       :title "Fetch Project- and Branch-Names"}
                      :chan resp-chan)
    (go (let [resp (<! resp-chan)]
          (when (= (:status resp) 200)
            (reset! project-and-branch-names* (:body resp)))))))


;;; fetch-commits ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def fetch-commits-id* (ratom/atom nil))

(defn fetch-commits []
  (let [search (-> js/window .-location .-search)
        path (str "/cider-ci/commits/")
        resp-chan (async/chan)
        id (request/send-off {:url (str path search) :method :get}
                             {:modal true ; TODO set to false and loop unconditionally
                              :title "Fetch Commits"}
                             :chan resp-chan)]
    (reset! fetch-commits-id* id)
    (go (let [resp (<! resp-chan)]
          ; TODO check if we are still on the commits page too
          (when (and (= id @fetch-commits-id*)
                     (= (:status resp) 200))
            ;(js/setTimeout fetch-commits 10000)
            (swap! state/client-state
                   assoc-in [:commits-page :fetch-request :response] resp))))))


;;; form ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn update-form-data [fun]
  (swap! state/client-state
         (fn [cs]
           (assoc-in cs [:commits-page :form-data]
                     (fun (-> cs :commits-page :form-data))))))

(defn update-form-data-value [k v]
  (update-form-data (fn [fd] (assoc fd k v))))

(defn heads-only-component []
  [:div.checkbox
   [:label
    [:input {:id :heads-only
             :type :checkbox
             :checked (-> @form-data* :heads-only)
             :on-change #(update-form-data (fn [fd] (assoc fd :heads-only (-> fd :heads-only not))))}]
    "Heads only"]])

(defn project-name-component []
  [:div.form-group
   [:label "Project Name" ]
   [:div.input-group
    [:input#project-name.form-control
     {:placeholder "My-Project"
      :on-change #(update-form-data-value :project-name (-> % .-target .-value presence))
      :value (-> @form-data* :project-name)
      }]
    [:span.input-group-addon
     [:input {:id :project-name-as-regex
              :type :checkbox
              :checked (-> @form-data* :project-name-as-regex boolean)
              :on-change #(update-form-data (fn [fd] (assoc fd :project-name-as-regex
                                                            (-> fd :project-name-as-regex boolean not))))}]
     " case insensitive regex "]]])

(defn branch-name-component []
  [:div.form-group
   [:label "Branch Name" ]
   [:div.input-group
    [:input#branch-name.form-control
     {:placeholder "master"
      :on-change #(update-form-data-value :branch-name (-> % .-target .-value presence))
      :value (-> @form-data* :branch-name)
      }]
    [:span.input-group-addon
     [:input {:id :branch-name-as-regex
              :type :checkbox
              :checked (-> @form-data* :branch-name-as-regex boolean)
              :on-change #(update-form-data (fn [fd] (assoc fd :branch-name-as-regex
                                                            (-> fd :branch-name-as-regex boolean not))))}]
     " case insensitive regex "]]])

(defn reset-component []
  [:div.col-xs-6
   [:a.btn.btn-warning
    {:href (routes/commits-path {:query-params {:Z (-> 0 clj->js js/JSON.stringify)}})}
    [:i.fa.fa-remove]
    " Reset "]])

(defn filter-component []
  [:div.col-xs-6
   [:a.pull-right.btn.btn-primary
    {:href (routes/commits-path
             {:query-params (->> @form-data*
                                 (map (fn [[k v]] [k (.stringify js/JSON (clj->js v))]))
                                 (into {}))})}
    [:i.fa.fa-filter]
    " Filter "]])

(defn form-component []
  [:div.form
   [heads-only-component]
   [project-name-component]
   [branch-name-component]
   [:div.form-group.row
    [reset-component]
    [filter-component]
    ]])


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def tree-commits (reaction (-> @state/client-state :commits-page :fetch-request :response :body)))

(defn project-link [project]
  [:a {:href (str "/cider-ci/repositories/projects/" (:id project))}
   [:b (:name project)] " "])

(defn project-filter [project]
  [:a.btn.btn-xs.btn-primary
   {:href (routes/commits-path
            {:query-params (-> @form-data*
                               (merge
                                 {:project-name (-> project :name clj->js js/JSON.stringify)
                                  :project-name-as-regex "false"})
                               (dissoc :branch-name)
                               (dissoc :branch-name-as-regex))})}
   [:i.fa.fa-filter] " "])

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
  [:a.btn.btn-xs.btn-primary
   {:href (routes/commits-path
            {:query-params (merge @form-data*
                                  {:project-name (-> project :name clj->js js/JSON.stringify)
                                   :project-name-as-regex "false"
                                   :branch-name (-> branch :name clj->js js/JSON.stringify)
                                   :branch-name-as-regex "false"})})}
   [:i.fa.fa-filter]])

(defn project-branches [project]
  [:span
   (doall (for [branch (doall (:branches project))]
            (let [headdist (:distance_to_head branch)]
              [:span
               {:key (str (:id project) "/" (:name branch))
                :style {:font-weight (if (= 0 headdist)
                                       "strong" "normal")}}
               " "
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


;;; page ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn debug-component []
  (when (:debug @state/client-state)
    [:div.page-debug
     [:hr.clearfix]
     [:h2 "Page Debug"]
     [:div
      [:h3 "@form-data"]
      (pre-component @form-data*)]
     [:div
      [:h3 "@project-and-branch-names*"]
      (pre-component @project-and-branch-names*)]
     [:h4 "Tree-Commits"]
     (pre-component @tree-commits)
     ]))

(defn post-mount-setup [component]
  (fetch-project-and-branchnames)
  (.tooltip (js/$ (reagent.core/dom-node component))))

(defn set-canonic-query-params [_]
  (let [current-query-paramerters @current-query-paramerters*
        canonic-query-prameters  (as-> @current-query-paramerters* cqp
                                   (dissoc cqp :Z)
                                   (assoc cqp :heads-only
                                          (cond (contains? cqp :heads-only) (-> cqp :heads-only boolean)
                                                :else true)))
        path (routes/commits-path {:query-params canonic-query-prameters})]


    (js/console.log (pprint {:current-query-paramerters* @current-query-paramerters*
                             :canonic-query-prameters canonic-query-prameters
                             :path path }))
    (accountant/navigate! path)))

(defn page []
  ; TODO Filter ähnlich Workspace page
  ; API liefert commits mit tree-id usw; jobs werden in dedizierten requests geholt
  ; Notifications gemäss Filer (wie soll das funktionierten?)
  ; Auch Tree-id notifications
  (reagent/create-class
    {:component-did-mount (fn [c]
                            (set-canonic-query-params c)
                            (post-mount-setup c))
     :reagent-render
     (fn []
       [:div.commits-page
        ;[:pre (-> @state/page-state :current-page :query-params str)]
        [:div.alert.alert-warning
         [:p "This page is a (partial functional) " [:b " prototype!"]]
         [:p [:span "You are probably looking for the  "]
          [:b [:a {:href "/cider-ci/ui/workspace" } "Workspace" ]]
          [:span " page."]]]
        [:h1 "Commits"]
        [form-component]
        [current-query-params-component]
        [:ul.tree-commits.list-unstyled
         (doall (for [tree-commit @tree-commits]
                  [:li {:key (:tree_id tree-commit)}(tree-commit-component tree-commit)]))]
        [debug-component]])}))
