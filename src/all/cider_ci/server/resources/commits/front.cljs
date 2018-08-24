; Copyright © 2013 - 2018 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.server.resources.commits.front
  (:refer-clojure :exclude [str keyword])
  (:require-macros
    [reagent.ratom :as ratom :refer [reaction]]
    [cljs.core.async.macros :refer [go]]
    )
  (:require
    [cider-ci.server.front.breadcrumbs :as breadcrumbs]
    [cider-ci.server.front.components :as components]
    [cider-ci.server.front.icons :as icons]
    [cider-ci.server.front.requests.core :as requests]
    [cider-ci.server.front.state :as state]
    [cider-ci.server.paths :as paths :refer [path]]
    [cider-ci.server.resources.commits.shared :refer [default-query-parameters]]

    [cider-ci.server.front.shared :as front-shared]

    ;[cider-ci.constants :as constants :refer [GRID-COLS]]
    ;[cider-ci.server.front.state :as state :refer [routing-state*]]

;    [cider-ci.server.client.connection.request :as request]
;    [cider-ci.server.client.routes :as routes]
;    [cider-ci.server.client.state :as state]
;    [cider-ci.server.client.constants :refer [CONTEXT]]
;    [cider-ci.server.client.shared :refer [pre-component]]
    [cider-ci.utils.core :refer [keyword str presence]]
;    [cider-ci.server.repository.ui.projects.shared :refer [humanize-datetime]]

    [accountant.core :as accountant]
    [cider-ci.utils.sha1]
    [cljs-http.client :as http]
    [cljs.core.async :as async]
    [cljs.core.async :refer [timeout]]
    [cljs.pprint :refer [pprint]]
    [clojure.contrib.inflect :refer [pluralize-noun]]
    [goog.string :as gstring]
    [goog.string.format]
    [reagent.core :as reagent]
    [reagent.ratom :as ratom]
    [secretary.core :as secretary :include-macros true]
    ))


(def current-query-paramerters* 
  (reaction (-> @state/routing-state* :query-params)))

(def current-query-paramerters-normalized*
  (reaction (merge default-query-parameters
           @current-query-paramerters*)))

(defn page-path-for-query-params [query-params]
  (path (:handler-key @state/routing-state*) 
        (:route-params @state/routing-state*)
        (merge @current-query-paramerters-normalized*
               query-params)))


;;; filters ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn text-input-component [data-key & [{placeholder :placeholder}]]
  (let [as-regex-key (-> data-key (str "-as-regex") keyword)
        as-regex-val (-> @current-query-paramerters-normalized*
                         (get as-regex-key) presence boolean)]
    [:div.form-group {:key data-key}
     [:div.input-group.mb-3
      [:div.input-group-prepend
       [:button.btn.btn-outline-secondary
        {:type :button
         :disabled (-> @current-query-paramerters-normalized*
                       (get data-key) presence boolean not)
         :on-click #(accountant/navigate! (page-path-for-query-params 
                                            {:page 1 data-key ""}))}
        icons/delete]]
      [:input.form-control
       {:id data-key
        :placeholder placeholder 
        :on-change (fn [e]
                     (let [val (or (-> e .-target .-value presence) "")]
                       (accountant/navigate! (page-path-for-query-params
                                               {:page 1 data-key val}))))
        :value (or (-> @current-query-paramerters-normalized* (get data-key) presence) "")
        }]
      
      [:div.input-group-append
       [:div.input-group-text
        [:label
         [:input
          {:type :checkbox
           :checked as-regex-val 
           :on-change #(accountant/navigate! 
                         (page-path-for-query-params
                           {:page 1 as-regex-key (not as-regex-val)}))}]
         " as regex "
         [:sup
          [:a.text-muted
           {:href "#"
            :title (->> ["If this is checked the input value will be mached as a case insensitive regular expression. "
                         " Otherwise the value will be compared as case sensitive string equality."]
                        clojure.string/join)
            :data-toggle "tooltip" :data-html "true"}
           [:i.fa.fa-question-circle]]]]]]]]))

(defn checkbox-component [data-key & [{label :label description :description}]]
  (let [current-val (-> @current-query-paramerters-normalized* (get data-key) presence boolean)]
    [:div.checkbox
     [:label
      [:input {:id :heads-only
               :type :checkbox
               :checked current-val
               :on-change #(accountant/navigate! 
                             (page-path-for-query-params
                               {:page 1 data-key (not current-val)}))}]
      label
      [:sup
       [:a.text-muted
        {:href "#"
         :title description
         :data-toggle "tooltip" :data-html "true"}
        [:i.fa.fa-question-circle]]]]]))

(defn filter-component []
  [:div.filters.form.card.bg-light.p-2
   [:div.row 
    [:div.col-md [text-input-component :project-name {:placeholder "Project name"}]]
    [:div.col-md [text-input-component :branch-name {:placeholder "Branch name"}]]]
   [:div.row
    [:div.col-md [text-input-component :email {:placeholder "Author or committer e-mail address"}]]
    [:div.col-md [text-input-component :git-ref {:placeholder "Git reference: commit or tree id"}]]]
   [:div.row
    [:div.col-md
     [checkbox-component :heads-only 
      {:label " Heads only "
       :description (->> [" This option will only show those commits "
                          " which are currently the head of a branch. " ]
                         clojure.string/join)}]]
    [:div.col-md
     [checkbox-component :my-commits
      {:label " My commits only "
       :description (->> ["This option will filter the commits by any e-mail address"
                          " associated with the currently signed in user."]
                         clojure.string/join)}]]
    [:div.col-md
     [:a.btn.btn-sm.btn-secondary.float-right
      {:href (page-path-for-query-params default-query-parameters )}
      icons/delete
      " Reset "]]]])


;;; some helpers ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn json-stringify-map-values [m]
  (->> m
       (map (fn [[k v]] [k (-> v clj->js js/JSON.stringify)]))
       (into {})))

(def filter-button
  [:span.fa-stack.fa-sm.text-muted
   {:style {:font-size "71%"}}
   [:i.fas.fa-square.fa-stack-2x]
   [:i.fas.fa-filter.fa-stack-1x.fa-inverse]])

;;; commits ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def fetch-commits-id* (reagent/atom nil))
(def tree-commits* (reagent/atom {}))

(defn fetch-commits [& args]
  (let [query-paramerters @current-query-paramerters-normalized*]
    (go (<! (timeout 200))
        (when (= query-paramerters @current-query-paramerters-normalized*)
          (let [resp-chan (async/chan)
                id (requests/send-off {:url (path :commits) :method :get
                                       :query-params query-paramerters}
                                      {:modal false
                                       :title "Fetch Commits"
                                       :handler-key :commits
                                       :retry-fn #'fetch-commits}
                                      :chan resp-chan)]
            (reset! fetch-commits-id* id)
            (go (let [resp (<! resp-chan)]
                  (when (and (= (:status resp) 200) ;success
                             (= id @fetch-commits-id*) ;still the most recent request
                             (= query-paramerters @current-query-paramerters-normalized*)) ;query-params have not changed yet
                    ;(reset! effective-query-paramerters* current-query-paramerters)
                    (swap! tree-commits* assoc 
                           (path (:handler-key @state/routing-state*) {} query-paramerters)
                           (->> (-> resp :body)
                                (map-indexed (fn [idx u]
                                               (assoc u :c idx)))))
                    (js/setTimeout
                      #(when (and (= id @fetch-commits-id*)
                                  (= :commits (:handler-key @state/routing-state*)))
                         (fetch-commits))
                      (* 10 1000))))))))))

(defn reload-component []
  (reagent/create-class
    {:component-did-mount fetch-commits
     :component-did-update fetch-commits
     :reagent-render
     (fn [_] [:div.current-query-parameters
              {:style {:display (if @state/debug?* :block :none)}}
              [:h3 "@current-query-paramerters-normalized*"]
              [components/pre-component @current-query-paramerters-normalized*]])}))

(defn commit-id [commit]
  [:span (str "\u2009" (->> commit :id (take 6) clojure.string/join) "\u2009")])

(defn e-mail-filter-component [email]
  [:a.text-muted
   {:href (path (-> @state/routing-state* :handler-key)
                {} (json-stringify-map-values
                     (-> @current-query-paramerters*
                         (assoc :email email)
                         (assoc :email-as-regex false)
                         (assoc :page 1))))}
   filter-button])

(defn author-committer [commit]
  (if (= (-> commit :committer_name)
         (-> commit :author_name))
    [:span
     [:a.author.committer
      {:style {:font-size "141%"}
       :href "#"
       :title (str " Authored and committed by "
                   (-> commit :author_name) " "
                   (front-shared/humanize-datetime-component
                     (-> commit :committer_date)))
       :data-toggle "tooltip" :data-html "true"}
      [:img {:style {:margin-top "0.41ex"}
             :src (-> commit :committer_email front-shared/gravatar-url)}]]
     [e-mail-filter-component (-> commit :committer_email)]]
    [:span
     [:a.author
      {:href "#"
       :title (str " Authored by "
                   (-> commit :author_name) " "
                   (front-shared/humanize-datetime-component
                     (-> commit :author_date)))
       :data-toggle "tooltip" :data-html "true"}
      [:img {:src (-> commit :author_email front-shared/gravatar-url)}]]
     [e-mail-filter-component (-> commit :author_email)]
     " / "
     [:a.committer
      {:href "#"
       :title (str " Committed by "
                   (-> commit :committer_name) " "
                   (front-shared/humanize-datetime-component
                     (-> commit :committer_date)))
       :data-toggle "tooltip" :data-html "true"}
      [:img {:src (-> commit :committer_email front-shared/gravatar-url)}]]
     [e-mail-filter-component (-> commit :committer_email)]]))

(defn project-remote-commit-link [commit project]
  (when-let [http-url (re-matches #"http.*" (:git_url project))]
    [:a {:key http-url
         :href (str (clojure.string/replace http-url #"\.git$" "")
                    "/commit/" (:id commit))}
     (case (:type project)
       "github" [:i.fa.fa-github]
       "gitlab" [:i.fa.fa-gitlab]
       [:i.fa.fa-git])]))

(defn project-link [project]
  [:a {:href (path :project {:project-id (:id project)} {})}
   [:b (:name project)] " "])

(defn project-filter-component [project]
  [:a {:href (path (:handler-key @state/routing-state*) 
                   {} (-> @current-query-paramerters-normalized*
                          (assoc :project-name (-> project :name))
                          (assoc :project-name-as-regex false)
                          (dissoc :branch-name)
                          (dissoc :branch-name-as-regex)
                          (assoc :page 1)
                          json-stringify-map-values))}
   filter-button])

(defn branch-filter-component [project branch]
  [:a {:href (path (:handler-key @state/routing-state*) 
                   {} (-> @current-query-paramerters-normalized*
                          (assoc :project-name (-> project :name))
                          (assoc :project-name-as-regex false)
                          (assoc :branch-name (-> branch :name))
                          (assoc :branch-name-as-regex false)
                          (assoc :page 1)
                          json-stringify-map-values))}
   filter-button])

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
   [:a.depth
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

(defn project-branches [project]
  [:span
   (doall (for [branch (doall (:branches project))]
            (let [headdist (:distance_to_head branch)]
              [:span
               {:key (str (:id project) "/" (:name branch))
                :style {:font-weight (if (= 0 headdist)
                                       "strong" "normal")}}
               "\u2003"
               (if (= 0 headdist)
                 [:b (branch-comp headdist branch)]
                 [:span (branch-comp headdist branch)]) " "
               (branch-filter-component project branch)
               ])))])

(defn project-col-component [project]
  (let [id (:id project)]
    [:div.col.row.project 
     [:div.col.project 
      (project-link project)
      (project-filter-component project)]
     [:div.col.branches 
      (project-branches project)
      ]]))

(defn commit-component [commit]
  (let  [id (:key commit)]
    [:div.row.commit
     {:key id
      :id id}
     [:div.col-sm-auto.commit-id 
      ;(doall (for [project (:projects commit)] [project-remote-commit-link commit project]))
      [:span {:style {:margin "0px"}} (commit-id commit)]]
     [:div.col-sm-auto.commited-at
      {:id (str "commited-at_" id)
       :key (str "commited-at_" id)}
      [:span.author-committer (author-committer commit)]
      [:span " "]
      [:span.commited-at (front-shared/humanize-datetime-component 
                           (:committer_date commit))]]
     [:div.col-sm-auto.commit-subject
      {:key (str "subject_" id)
       :id (str "subject_" id)}
      [:em (:subject commit)]]
     [:div.col-sm-auto.row.projects
      (for [project (:projects commit)]
        [project-col-component project])]]))

(defn tree-commit-component [tree-commit]
  (let [tree-id (:tree_id tree-commit)]
    [:div.row.tree-commit
     {:key tree-id :id tree-id}
     [:div.tree-id.col-sm-auto
      {:key (str "tree-id-" tree-id)
       :id (str "tree-id-" tree-id)}
      [:span
       [:a.tree-id
        {:href (path :tree {:tree-id tree-id} {})}
        [:i.fa.fa-tree]
        "\u202F"
        [:span.git-ref.tree-id (->> tree-id (take 6) clojure.string/join)]
        ]]]
     [:div.commits.col-sm-auto
      (for [commit  (->> (:commits tree-commit)
                         (map #(assoc % :key (str "commit_" (:id %)))))]
        [commit-component commit])
      ]
     ]))

(defn trees-component []
  [:div.mt-3
   (if-let [tree-commits (get @tree-commits*
                              (path (:handler-key @state/routing-state*) {} @current-query-paramerters-normalized*))]
     [:div.tree-commits
      (doall (for [tree-commit tree-commits]
               (tree-commit-component tree-commit)))]
     [front-shared/please-wait-component])])

(defn debug-component []
  (when (:debug @state/global-state*)
    [:section.debug
     [:hr]
     [:h2 "Page Debug"]
     [:div
      [:h3 "@current-query-paramerters-normalized*"]
      [:pre (with-out-str (pprint @current-query-paramerters-normalized*))]]
     [:div
      [:h3 "@tree-commits*"]
      [:pre (with-out-str (pprint @tree-commits*))]]]))

(defn page []
  [:div.commits-page
   (breadcrumbs/nav-component
     [(breadcrumbs/home-li)
      (breadcrumbs/commits-li)][])
   [:h1 " Commits "]
   [filter-component]
   [reload-component]
   ; [tree-ids-component]
   ; [current-query-params-component]
   [trees-component]
   ; [pagination-component]
   [debug-component]
   ])

(defn event-handler [event]
  ;(js/console.log (with-out-str (pprint event)))
  (fetch-commits))

