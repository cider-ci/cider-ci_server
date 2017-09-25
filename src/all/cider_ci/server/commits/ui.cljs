(ns cider-ci.server.commits.ui
  (:refer-clojure :exclude [str keyword])
  (:require-macros
    [reagent.ratom :as ratom :refer [reaction]]
    [cljs.core.async.macros :refer [go]]
    )
  (:require
    [cider-ci.constants :as constants :refer [GRID-COLS]]
    [cider-ci.server.client.connection.request :as request]
    [cider-ci.server.client.routes :as routes]
    [cider-ci.server.client.state :as state]
    [cider-ci.server.client.constants :refer [CONTEXT]]
    [cider-ci.server.client.shared :refer [pre-component]]
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

(declare page fetch-commits tree-commits* tree-ids*)

(defn page-is-active? []
  (= (-> @state/page-state :current-page :component)
     "cider-ci.server.commits.ui/page"))

(def form-data* (reaction (-> @state/client-state :commits-page :form-data)))

(def filter-button
  [:span.fa-stack {:style {:font-size "100%"}}
   [:i.fa.fa-square-o.fa-stack-2x]
   [:i.fa.fa-filter.fa-stack-1x]])

;;; some helpers ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn gravatar-url [email]
  (->> email
       clojure.string/trim
       clojure.string/lower-case
       cider-ci.utils.sha1/md5-hex
       (gstring/format "https://www.gravatar.com/avatar/%s?s=32&d=retro")))

(defn json-stringify-map-values [m]
  (->> m
       (map (fn [[k v]] [k (-> v clj->js js/JSON.stringify)]))
       (into {})))

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

;;; effective query parameters ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def effective-query-paramerters* (ratom/atom {}))


;;; fetch-tree-commits ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def tree-commits*
  "Map of tree-commits keyed under the query-params they were fetched for."
  (ratom/atom {}))

(def current-tree-commits*
  "The tree-commits* for the @current-query-paramerters*"
  (reaction (get @tree-commits* @current-query-paramerters* [])))

(def fetch-commits-id*
  "An id to keep track of the most recent and thus (maybe) not outdated request."
  (ratom/atom nil))

(defn fetch-commits
  ([] (fetch-commits 1))
  ([counter]
   (let [current-query-paramerters @current-query-paramerters*
         query-params (-> current-query-paramerters
                          json-stringify-map-values)
         path (str "/cider-ci/commits/")
         resp-chan (async/chan)
         id (request/send-off {:url path :method :get :query-params query-params}
                              {:modal false
                               :title "Fetch Commits"}
                              :chan resp-chan)]
     (reset! fetch-commits-id* id)
     (go (let [resp (<! resp-chan)]
           (when (and (= (:status resp) 200) ;success
                      (= id @fetch-commits-id*) ;still the most recent request
                      (= current-query-paramerters @current-query-paramerters*)) ;query-params have not changed yet
             (reset! effective-query-paramerters* current-query-paramerters)
             (swap! tree-commits* assoc-in [current-query-paramerters] (:body resp)))
           (js/setTimeout
             #(when (and (page-is-active?)
                         (= id @fetch-commits-id*))
                (fetch-commits (inc counter)))
             (min 60000 (* counter counter 1000))))))))


;;; fetch-jobs-summaries ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def jobs-summaries* (ratom/atom {}))

(def fetch-jobs-summaries-id* (ratom/atom nil))

(defn fetch-jobs-summaries [& _]
  (let [path (str "/cider-ci/commits/jobs-summaries/")
        resp-chan (async/chan)
        id (request/send-off {:url path :method :get
                              :query-params {:tree-ids (-> @tree-ids* clj->js js/JSON.stringify)}}
                             {:modal false
                              :title "Fetch Summaries of Jobs"}
                             :chan resp-chan)]
    (reset! fetch-jobs-summaries-id* id)
    (go (let [resp (<! resp-chan)]
          (when (= (:status resp) 200)
            (swap! jobs-summaries* merge (:body resp)))
          (js/setTimeout
            #(when (and (page-is-active?)
                        (= id @fetch-jobs-summaries-id*))
               (fetch-jobs-summaries))
            5000)))))

(def tree-ids* (reaction (->> (get @tree-commits* @current-query-paramerters* [])
                              (map :tree_id)
                              set)))

(defn tree-ids-component []
  (reagent/create-class
    {:component-did-update fetch-jobs-summaries
     :component-did-mount fetch-jobs-summaries
     :reagent-render
     (fn [c]
       [:div {:style {:display :none}}
        [:h2 "Tree IDs"]
        (pre-component @tree-ids*)
        ])}))


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
    " Heads only "
    [:sup
     [:a.text
      {:href "#"
       :title (->> ["This option will only show those commits "
                    " which are currently the head of a branch." ]
                   clojure.string/join)
       :data-toggle "tooltip" :data-html "true"}
      [:i.fa.fa-question-circle]]]]])

(defn my-commits-component []
  (let [my-commits (-> @form-data* :my-commits presence boolean)]
    [:div.checkbox
     [:label
      [:input {:id :heads-only
               :type :checkbox
               :checked my-commits
               :on-change #(update-form-data (fn [fd] (assoc fd :my-commits (not my-commits))))}]
      " My commits "
      [:sup
       [:a.text
        {:href "#"
         :title (->> ["This option will filter the commits by any e-mail address"
                      " defined for the currently signed in user."]
                     clojure.string/join)
         :data-toggle "tooltip" :data-html "true"}
        [:i.fa.fa-question-circle]]]]]))

(defn text-input-component [form-data-key & {placeholder :placeholder}]
  (let [as-regex-key (-> form-data-key (str "-as-regex") keyword)]
    [:div.form-group {:key form-data-key}
     [:div.input-group
      [:a.text.input-group-addon
       {:on-click (fn [e]
                    (update-form-data-value form-data-key nil)
                    (.preventDefault e))}
       constants/utf8-erase-to-right]
      [:input#branch-name.form-control
       {:placeholder placeholder
        :on-change #(update-form-data-value form-data-key (-> % .-target .-value presence))
        :value (-> @form-data* form-data-key)
        }]
      [:span.input-group-addon
       [:label
        [:input {:id :branch-name-as-regex
                 :type :checkbox
                 :checked (-> @form-data* as-regex-key boolean)
                 :on-change #(update-form-data
                               (fn [fd] (assoc fd as-regex-key
                                               (-> fd as-regex-key boolean not))))}]
        " as regex "
        [:sup
         [:a.text
          {:href "#"
           :title (->> ["This option will match the input as a case insensitive regular expression. "
                        " The default is to match the input by string equality."]
                       clojure.string/join)
           :data-toggle "tooltip" :data-html "true"}
          [:i.fa.fa-question-circle]]]
        ]]]]))

(defn email-component []
  [:div.form-group
   [:div.input-group
    [:a.text.input-group-addon
     {:href "#"
      :on-click #(update-form-data-value :email nil)}
     constants/utf8-erase-to-right]
    [:input#branch-name.form-control
     {:placeholder "Author or committer e-mail address"
      :on-change #(update-form-data-value :email (-> % .-target .-value presence))
      :value (-> @form-data* :email)
      }]
    [:span.input-group-addon
     [:input {:id :branch-name-as-regex
              :type :checkbox
              :checked (-> @form-data* :email-as-regex boolean)
              :on-change #(update-form-data (fn [fd] (assoc fd :email-as-regex
                                                            (-> fd :email-as-regex boolean not))))}]
     " as regex "]]])

(defn reset-component []
  (let [disabled (not= @current-query-paramerters* @effective-query-paramerters*)
        waiting disabled]
    [:a.btn.btn-warning
     {:class (when disabled "disabled")
      :style {:width "100%" :margin-bottom "1em"}
      :href (routes/commits-path {:query-params {:heads-only true}})}
     (if waiting
       [:i.fa.fa-spinner.fa-pulse.fa-fw]
       [:i.fa.fa-remove.fa-fw])
     " Reset "]))

(defn filter-component []
  (let [disabled (not= @current-query-paramerters* @effective-query-paramerters*)
        waiting disabled]
    [:a.btn.btn-primary
     {:class (when disabled "disabled")
      :style {:width "100%" :margin-bottom "1em"}
      :href (routes/commits-path
              {:query-params
               (-> @form-data*
                   (assoc :page 1)
                   json-stringify-map-values)})}
     (if waiting
       [:i.fa.fa-spinner.fa-pulse.fa-fw]
       [:i.fa.fa-filter.fa-fw])
     " Filter "]))

(defn form-component []
  [:div.well {:style {:padding "1em" :padding-bottom "0px"}}
   [:div.form {:style {:margin-bottom "0px"}}
    [:div.row
     [:div {:class (str "col-md-" (Math/floor (/ GRID-COLS 2)))}
      [text-input-component :project-name :placeholder "Project name"]]
     [:div {:class (str "col-md-" (Math/floor (/ GRID-COLS 2)))}
      [text-input-component :branch-name :placeholder "Branch name"]]]
    [:div.row
     [:div {:class (str "col-md-" (Math/floor (/ GRID-COLS 2)))}
      [text-input-component :email :placeholder "Author or committer e-mail address"]]
     [:div {:class (str "col-md-" (Math/floor (/ GRID-COLS 2)))}
      [text-input-component :git-ref :placeholder "Git reference: commit or tree id"]]]
    [:div.row
     [:div {:class (str "col-md-" (Math/floor (/ GRID-COLS 8)))} [heads-only-component]]
     [:div {:class (str "col-md-" (Math/floor (/ GRID-COLS 8)))} [my-commits-component]]
     [:div {:class (str "col-md-" (Math/floor (/ GRID-COLS 2)))} ]
     [:div {:class (str "col-md-" (Math/floor (/ GRID-COLS 8)))} [reset-component]]
     [:div {:class (str "col-md-" (Math/floor (/ GRID-COLS 8)))} [filter-component]]]]])


;;; filter components ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn project-filter-component [project]
  [:a {:href
       (routes/commits-path
         {:query-params
          (json-stringify-map-values
            (-> @current-query-paramerters*
                (assoc :project-name (-> project :name))
                (assoc :project-name-as-regex false)
                (dissoc :branch-name)
                (dissoc :branch-name-as-regex)
                (assoc :page 1)))})}
   filter-button])

(defn e-mail-filter-component [email]
  [:a
   {:href
    (routes/commits-path
      {:query-params
       (json-stringify-map-values
         (-> @current-query-paramerters*
             (assoc :email email)
             (assoc :email-as-regex false)
             (assoc :page 1)))})}
   filter-button])

(defn branch-filter-component [project branch]
  [:a
   {:href
    (routes/commits-path
      {:query-params
       (json-stringify-map-values
         (-> @current-query-paramerters*
             (assoc :project-name (-> project :name))
             (assoc :project-name-as-regex false)
             (assoc :branch-name (-> branch :name))
             (assoc :branch-name-as-regex false)
             (assoc :page 1)))})}
   filter-button])


;;; main components ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn project-link [project]
  [:a {:href (str "/cider-ci/repositories/projects/" (:id project))}
   [:b (:name project)] " "])

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

(defn commit-id [commit]
  [:span (str "\u2009" (->> commit :id (take 6) clojure.string/join) "\u2009")])

(defn author-committer [commit]
  (if (= (-> commit :committer_name)
         (-> commit :author_name))
    [:span
     [:a.author.committer
      {:style {:font-size "141%"}
       :href "#"
       :title (str " Authored and committed by "
                   (-> commit :author_name) " "
                   (humanize-datetime (:timestamp @state/client-state)
                                      (-> commit :committer_date)))
       :data-toggle "tooltip" :data-html "true"}
      [:img {:style {:margin-top "0.41ex"}
             :src (-> commit :committer_email gravatar-url)}]]
     [e-mail-filter-component (-> commit :committer_email)]]
    [:span
     [:a.author
      {:href "#"
       :title (str " Authored by "
                   (-> commit :author_name) " "
                   (humanize-datetime (:timestamp @state/client-state)
                                      (-> commit :author_date)))
       :data-toggle "tooltip" :data-html "true"}
      [:img {:src (-> commit :author_email gravatar-url)}]]
     [e-mail-filter-component (-> commit :author_email)]
     " / "
     [:a.committer
      {:href "#"
       :title (str " Committed by "
                   (-> commit :committer_name) " "
                   (humanize-datetime (:timestamp @state/client-state)
                                      (-> commit :committer_date)))
       :data-toggle "tooltip" :data-html "true"}
      [:img {:src (-> commit :committer_email gravatar-url)}]]
     [e-mail-filter-component (-> commit :committer_email)]]))

(defn commited-at [commit]
  (humanize-datetime (:timestamp @state/client-state)
                     (-> commit :committer_date)))

(defn projects-component [commit remaining-cols]
  (let [project-cols 3
        branches-cols (- remaining-cols project-cols)]
    [:div
     (doall
       (for [project (:projects commit)]
         (let [id (:id project)]
           [:div.row.project {:key id :id id :style {:margin "0px"}}
            [:div {:key (str "project-" id)
                   :id (str "project-" id)
                   :class (str "col-sm-" project-cols)}
             (project-link project)
             (project-filter-component project)]
            [:div {:key (str "branches-" id)
                   :id (str "branches-" id)
                   :class (str "col-sm-" branches-cols)}
             (project-branches project)]])))]))

(defn commit-component [commit remaining-cols]
  (let  [commit-cols 3
         commited-cols 4
         subject-cols 5
         id (:key commit)]
    [:div.row.commit
     {:key id
      :id id
      :style {:margin "0px"}}
     [:div.commit-id {:class (str "col-sm-" commit-cols)}
      (doall (for [project (:projects commit)]
               [project-remote-commit-link commit project]))
      [:span {:style {:margin "0px"}} (commit-id commit)]]
     [:div.commited-at
      {:id (str "commited-at_" id)
       :key (str "commited-at_" id)
       :class (str "col-sm-" commited-cols)}
      [:span.author-committer (author-committer commit)]
      [:span " "]
      [:span.commited-at (commited-at commit)]]
     [:div.commit-subject
      {:key (str "subject_" id)
       :id (str "subject_" id)
       :class (str "col-sm-" subject-cols)}
      [:em (:subject commit)]]
     [projects-component commit
      (- remaining-cols commited-cols commited-cols subject-cols)]]))

(defn commits-component [tree-commit remaining-cols]
  (let [id (str "commits_" (:tree_id tree-commit))]
    [:div {:key id
           :id id
           :class (str "col-sm-" remaining-cols)}
     (doall
       (for [commit  (->> (:commits tree-commit)
                          (map #(assoc % :key (str "commit_" (:id %)))))]
         [commit-component commit remaining-cols]))]))

(def summary-job-states (->> cider-ci.constants/STATES :JOB (map keyword)))

(defn tree-commit-component [tree-commit remaining-cols]
  (let [tree-id-cols 3
        id (:tree_id tree-commit)
        jobs-summaries (get @jobs-summaries* (keyword id) {})]
    [:div.row.tree-commit
     {:key id :id id
      :style {:margin-bottom "1em"}}
     [:div {:key (str "tree-id-" id)
            :id(str "tree-id-" id )
            :class (str "col-sm-" tree-id-cols)}
      [:span
       [:a.tree-id
        {:href (routes/tree-path {:tree-id (:tree_id tree-commit)})}
        [:i.fa.fa-tree.text-muted]
        [:span.git-ref.tree-id (->> tree-commit :tree_id (take 6) clojure.string/join)]
        [:span "\u2008"]
        (doall
          (for [state summary-job-states]
            (when-let [count (get jobs-summaries state nil)]
              [:span {:key state} [:span.label {:class (str "label-" state)} count] "\u2008"])))]]]
     [commits-component tree-commit (- GRID-COLS tree-id-cols)]]))

(defn trees-component []
  [:div.tree-commits
   (doall (for [tree-commit (get @tree-commits* @current-query-paramerters* [])]
            (tree-commit-component tree-commit GRID-COLS)))])


;;; pagination ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn pagination-component []
  (let [disabled (not= @current-query-paramerters* @effective-query-paramerters*)
        waiting (not= @current-query-paramerters* @effective-query-paramerters*)]
    [:div.clearfix
     (when (< 1 (:page @current-query-paramerters*))
       [:div.pull-left
        [:a.btn.btn-info
         {:class (when disabled "disabled")
          :href (routes/commits-path
                  {:query-params (-> @current-query-paramerters*
                                     (assoc :page (- (:page @current-query-paramerters*) 1))
                                     json-stringify-map-values)})}
         (if waiting
           [:i.fa.fa-spinner.fa-pulse.fa-fw]
           [:i.fa.fa-arrow-circle-left.fa-fw])
         " Previous page "
         ]])
     [:div.pull-right
      [:a.btn.btn-info
       {:class (when disabled "disabled")
        :href (routes/commits-path
                {:query-params (-> @current-query-paramerters*
                                   (assoc :page (+ (:page @current-query-paramerters*) 1))
                                   json-stringify-map-values)})}
       " Next page "
       (if waiting
         [:i.fa.fa-spinner.fa-pulse.fa-fw]
         [:i.fa.fa-arrow-circle-right.fa-fw])]]]))

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
      [:h3 "@current-query-paramerters*"]
      (pre-component @current-query-paramerters*)]
     [:div
      [:h3 "@effective-query-paramerters*"]
      (pre-component @effective-query-paramerters*)]
     [:div
      [:h4 "@tree-ids"]
      (pre-component @tree-ids*)]
     [:div
      [:h4 "@jobs-summaries*"]
      (pre-component @jobs-summaries*)]
     [:div
      [:h4 "@tree-commits*"]
      (pre-component @tree-commits*)]
     ]))

(defn post-mount-setup [component]
  (.tooltip (js/$ (reagent.core/dom-node component))))

(defn page-will-unmount [& args]
  (reset! fetch-commits-id* nil)
  (reset! fetch-jobs-summaries-id* nil))

(defn page []
  (reagent/create-class
    {:component-did-mount (fn [c] (post-mount-setup c))
     :component-will-unmount page-will-unmount
     :reagent-render
     (fn []
       [:div.commits-page
        [:h1 "Commits"]
        [form-component]
        [tree-ids-component]
        [current-query-params-component]
        [trees-component]
        [pagination-component]
        [debug-component]])}))
