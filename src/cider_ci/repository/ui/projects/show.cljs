(ns cider-ci.repository.ui.projects.show
  (:require-macros
    [reagent.ratom :as ratom :refer [reaction]]
    [cljs.core.async.macros :refer [go]]
    )
  (:require
    [cider-ci.repository.ui.request :as request]

    [cider-ci.repository.branches.ui :as branches]
    [cider-ci.repository.fetch-and-update.ui :as fetch-and-update]
    [cider-ci.repository.push-hooks.ui :as push-hooks]
    [cider-ci.repository.push-notifications.ui :as push-notifications]
    [cider-ci.repository.status-pushes.ui :as status-pushes]
    [cider-ci.repository.ui.projects.orientation :as orientation]
    [cider-ci.repository.ui.projects.shared :as projects-components]

    [cider-ci.repository.constants :refer [CONTEXT]]
    [cider-ci.client.state :as state]

    [cider-ci.utils.url]

    [secretary.core :as secretary :include-macros true]
    [accountant.core :as accountant]
    [cljs.pprint :refer [pprint]]
    [reagent.core :as r]))


(declare page)

(secretary/defroute project-path (str CONTEXT "/projects/:id") {id :id}
  (swap! state/page-state assoc :current-page
         {:component #'page :id id}))

(def id (reaction (-> @state/page-state :current-page :id)))

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
                                 [:div [:strong "Fetch and update has been triggered."]]]
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
                         [:i.fa.fa-pencil] " Edit"])]]))

(defn title []
  (if @project
    [:div
     [actions]
     [:h1 " Project \"" (:name @project) "\""]]
    [:div
     [:h2.text-warning "Project not found"]
     [:p.text-warning "We do not know a project with the id "
      [:code @id] "."]
     ]))

(defn bare-properties []
  [:section.bare-properties
   [:h2 "Bare Properties"]
   [:table.table.table-striped
    [:thead]
    [:tbody
     (for [[k v]  (sort-by (fn [[k _]] k) @project)]
       (let [id (str "field_" (str k))]
         [:tr {:id id :key id}
          [:td {:id (str "key_" id)} k]
          [:td {:id (str "value_" id)} (.stringify js/JSON (clj->js v))]]))]]])

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
  [:div.summary
   [branches/page-section @project]
   [fetch-and-update/page-section @project]
   [push-notifications/page-section @project]
   [push-hooks/page-section @project]
   [status-pushes/page-section @project]
   ])

(defn table [project]
  [:div
   [:table.table.table-striped.table-project
    [:thead
     [:tr
      [branches/th]
      [fetch-and-update/th]
      [push-notifications/th]
      [push-hooks/th]
      [status-pushes/th]]]
    [:tbody.table-bordered
     [:tr
      [branches/td project]
      [fetch-and-update/td project]
      [push-notifications/td project]
      [push-hooks/td project]
      [status-pushes/td project]
      ]]]])

(defn page []
  (r/create-class
    {:component-will-mount reset-state!
     :reagent-render
     (fn []
       [:div.project
        [:div.row.orientation
         [:div.col-md-6 [orientation/breadcrumbs {:id @id}]]]
        [errors]
        [fetch-and-update/error-alert @project]
        [request-response-component]
        [title]
        [table @project]
        [summary]
        [bare-properties]
        [debug]
        ])}))

(defn issue []
  (r/create-class
    {:component-will-mount reset-state!
     :reagent-render
     (let [ks (-> @state/page-state :current-page :issue-keys)
           issue (-> @state/server-state
                     :repositories (get (keyword @id)) (get-in ks))]
       (fn []
         [:div.project-issue
          [orientation/breadcrumbs {:id @id}]
          [:h1.text-danger "Issue \"" (with-out-str (pprint ks)) "\""]
          [:pre (.stringify js/JSON (clj->js issue) nil 2)]
          ]))}))

