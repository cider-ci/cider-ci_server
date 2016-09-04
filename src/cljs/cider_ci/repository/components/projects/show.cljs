(ns cider-ci.repository.components.projects.show
  (:require-macros
    [reagent.ratom :as ratom :refer [reaction]]
    [cljs.core.async.macros :refer [go]]
    )
  (:require
    [cider-ci.repository.request :as request]

    [cider-ci.repository.constants :refer [CONTEXT]]
    [cider-ci.repository.state :as state]
    [cider-ci.repository.components.projects.table :as table]

    [cider-ci.utils.url]

    [accountant.core :as accountant]
    [cljs.pprint :refer [pprint]]
    [reagent.core :as r]
    ))


(def id (reaction (-> @state/client-state :current-page :id)))

(def project (reaction (-> @state/server-state :repositories (get (keyword @id)))))

(def admin? (reaction (-> @state/server-state :user :is_admin)))

(def request-response (r/atom {}))

(defn reset-state! []
  (reset! request-response {}))

(defn delete-project []
  (let [req {:method :delete
             :url (str CONTEXT "/projects/" @id)}]
    (request/send-off req [{:db request-response :keys []}])))

(defn fetch []
  (let [req {:url (str CONTEXT "/projects/" @id "/fetch")}]
    (request/send-off req [; {:db state/client-state :keys [:last-triggered-fetch]}
                           {:db request-response :keys []}])))

(defn request-response-component []
  [:div.request-response
   (let [request-response @request-response
         status (-> request-response :response :status)
         pre-str (with-out-str (pprint (dissoc request-response :headers :trace-redirects)))]
     (cond (= status 204) (let [path (str CONTEXT "/projects/")]
                            (accountant/navigate! path)
                            [:div.alert.alert-success
                             [:div [:strong "The project has been deleted."]]])
           (and (= (-> request-response :response :body :state) "fetching")
                (= status 202)) [:div.alert.alert-success
                                 [:button.close {:type "button" :on-click reset-state!} [:span [:i.fa.fa-close]]]
                                 [:div [:strong "Fetching has been triggered."]]]
           status [:div.alert.alert-warning
                   [:button.close {:type "button" :on-click reset-state!} [:span [:i.fa.fa-close]]]
                   [:div [:strong "Oh snap! Something unexpected happened. "]
                    "You might want to try again if the error appears to be transient."]
                   [:pre pre-str]]
           (:request request-response) [:div.alert.alert-default
                             [:button.close {:type "button" :on-click reset-state!} [:span [:i.fa.fa-close]]]
                             [:pre pre-str]]
           :else nil))])

(defn actions []
  (when @project
    [:ul.actions.list-inline.pull-right
     [:li (when @admin? [:button.btn.btn-danger
                         {:on-click delete-project}
                         [:i.fa.fa-trash]
                         " Delete"])]
     [:li (when @admin? [:a.btn.btn-warning
                         {:href  (str CONTEXT "/projects/" (:id @project) "/edit")}
                         [:i.fa.fa-pencil] " Edit"])]
     [:li [:a.btn.btn-default
           {:on-click fetch}
           [:i.fa.fa-refresh] " Fetch"]]]))

(defn title []
  (if @project
    [:div
     [actions]
     [:h1 [table/state-icon @project]
      " Project \"" (:name @project) "\""]]
    [:div
     [:h2.text-warning "Project not found"]
     [:p.text-warning "We do not know a project with the id "
      [:code @id] "."]
     ]))

(defn breadcrumbs [params]
  (fn [params]
    (let [{id :id issue-key :issue-key} params]
      [:ol.breadcrumb
       [:li [:a {:href "/cider-ci/ui/public"} "Home"]]
       [:li [:a {:href (str CONTEXT "/projects/")} "Projects"]]
       (when id
         [:li [:a {:href (str CONTEXT "/projects/" id)} "Project"]])
       (when (and id issue-key)
         [:li [:a {:href (str CONTEXT "/projects/" id "/issues/" issue-key)} "Issue"]])
       ])))

(defn bare-properties []
  (when (:debug @state/client-state)
    [:section.bare-properties
     [:h2 "Bare Properties"]
     [:table.table.table-striped
      [:thead]
      [:tbody
       (for [[k v]  (sort-by (fn [[k _]] k) @project)]
         (let [id (str "field_" (str k))]
           [:tr {:id id :key id}
            [:td {:id (str "key_" id)} k]
            [:td {:id (str "value_" id)} v]]))]]]))

(defn debug []
  (when (:debug @state/client-state)
    [:div
     [:hr]
     [:h2 "Local State Debug"]
     [:pre
      (with-out-str
        (pprint
          {:id @id
           :request-response @request-response
           :project @project}))]]))

(defn errors []
  [:div.errors
   (doall
     (for [[k issue] (sort-by (fn [[k _]] k)(:issues @project))]
       [:div.alert.alert-danger {:id k :key k}
        [:b (:title issue)]
        (when-let [desc (:description issue)]
          [:p
           [:code (apply str (take 80 desc))]
           (when (> (count desc) 80)
             [:a.alert-link
              {:href (str CONTEXT "/projects/" @id "/issues/" (name k))}
              " ... more"])])]))])

(defn summary []
  (fn []
    (let [now-timestap (:timestamp @state/client-state)
          last-fetched-at (:last_fetched_at @project)
          last-fetched-at-ago (when (and now-timestap last-fetched-at)
                                (.to now-timestap last-fetched-at))
          last-fetch-failed-at (:last_fetch_failed_at @project)
          last-fetch-failed-at-ago (when last-fetch-failed-at (.to (js/moment) last-fetch-failed-at))
          update-notification-received-at-ago (when-let [ts (:update_notification_received_at @project)]
                                               (.to (js/moment) ts))]
      [:div.summary

       [:p "The remote URL for this project is " [:code (:git_url @project)] ". "]

       [:p
        (when last-fetched-at-ago
          [:span.text-success "The project has been " [:b "fetched " last-fetched-at-ago ]
           " and the branches have been updated. "])
        (when last-fetch-failed-at-ago
          [:span.text-warning "The last failed fetch happened " last-fetch-failed-at-ago ". "])]

       [:p
        (if update-notification-received-at-ago
          [:span.text-success "The last update-notification from the remove was received "
           update-notification-received-at-ago ". "]
          [:span.text-warning "We received no update-notification since this services has been started. "])
        (when @admin?
          (when-let [update-token (:update_notification_token @project)]
            (let [url (str (-> @state/server-state :config :server_base_url) CONTEXT
                           "/update-notification/" update-token)]
              [:span "The URL to post update-notifications to is "
               [:a {:href url :method "POST"}
                [:code#update_notification_url url]]"."])))]


       ])))

(defn page []
  (r/create-class
    {:component-will-mount reset-state!
     :reagent-render
     (fn []
       [:div.project
        [breadcrumbs {:id @id}]
        [errors]
        [request-response-component]
        [title]
        [summary]
        [bare-properties]
        [debug]
        ])}))

(defn issue []
  (r/create-class
    {:component-will-mount reset-state!
     :reagent-render
     (let [issue-key (-> @state/client-state :current-page :issue-key)
           issue (-> @state/server-state :repositories (get (keyword @id))
                     :issues (get (keyword issue-key)))]
       (fn []
         [:div.project-issue
          [breadcrumbs {:id @id :issue-key issue-key}]
          [:h1.text-danger "Issue \"" (:title issue) "\""]
          [:pre (:description issue)]
          [debug]
          ]))}))

