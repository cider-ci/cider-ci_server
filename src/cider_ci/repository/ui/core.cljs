(ns cider-ci.repository.ui.core
  (:require
    [cider-ci.repository.ui.navbar :as navbar]
    [cider-ci.repository.ui.debug]
    [cider-ci.repository.ui.projects.edit-new :as projects.edit-new]
    [cider-ci.repository.ui.projects.index :as projects.index]
    [cider-ci.repository.ui.projects.show :as projects.show]
    [cider-ci.repository.constants :refer [CONTEXT]]
    [cider-ci.repository.ui.request :as request]
    [cider-ci.repository.ui.state :as state]
    [cider-ci.repository.ui.projects.shared :refer [humanize-datetime]]

    [cljsjs.jquery]
    [cljsjs.bootstrap]

    [cider-ci.utils.digest :refer [digest]]

    [goog.string :as gstring]
    [goog.string.format]

    [reagent.core :as reagent]
    [secretary.core :as secretary :include-macros true]
    [accountant.core :as accountant]

    [cljs.pprint :refer [pprint]]

    ))

;; -------------------------
;; Views

(defn clj->json
  [ds]
  (.stringify js/JSON (clj->js ds)))

(defn general-debug-section []
  (when (:debug @state/client-state)
    [:section.debug
     [:hr]
     [:h1 "Debug"]
     [:div.server-db
      [:h2 "Server State"]
      [:pre (.stringify js/JSON (clj->js @state/server-state) nil 2)]]
     [:div.client-db
      [:h2 "Client State"]
      [:pre (.stringify js/JSON (clj->js @state/client-state) nil 2)]
      ]]))

(defn sync-alert []
  [:div#sync-alert
   (when false ; DISABLED !
     (when-not (:server-state-is-in-sync @state/client-state)
       [:div.alert.alert-danger
        [:strong "Bummer! "]
        "It seems that the state in your browser and the state
        on the server got out of sync. "
        [:strong "Please reload this page at your convenience manually!"]]))])

(defn connection-status []
  [:div.connection-status
   (cond
     (-> @state/client-state
         :connection :ever-opened? not) [:div.alert.alert-warning
                                                 [:h4 [:i.fa.fa-spinner.fa-spin] " Connecting "]
                                                 [:p "Please wait a few seconds. "
                                                  "Check your connection and browser. Finally try to reload the whole page. "
                                                  "Contact the application administrator if the problem persists. " ]]
     (-> @state/client-state
         :connection :open? not) [:div.alert.alert-danger
                              [:h4 [:i.fa.fa-spinner.fa-spin] " Lost Connection "]
                              [:p "We are trying to reestablish the connection. Please wait a few seconds. "
                               "Check your connection and browser. Finally try to reload the whole page. " ]]
     :else [:div]
     )])

(defn current-page []
  [:div.container-fluid
   [navbar/navbar]
   [connection-status]
   (if (:server_state_updated_at @state/client-state)
     [:div
      [sync-alert]
      [:div.page [(-> @state/client-state :current-page :component)]]]
     [:div
      [:div.progress
       [:div.progress-bar.progress-bar-warning.progress-bar-striped.active
        {:style {:width "100%"}}]]
      [:h1.text-warning "Waiting for Data"]
      [:p.text-warning  "Please stand by."]])
   [general-debug-section]
   [request/requests]])

(defn not-found []
  [:h1 " 404 - Not Found"])

;; -------------------------
;; Routes


(secretary/defroute (str CONTEXT "/projects/") [query-params]
  (swap! state/client-state assoc :current-page
         {:component #'projects.index/page
          :query-params query-params
          }))

(secretary/defroute (str CONTEXT "/projects/new") []
  (swap! state/client-state assoc :current-page
         {:component #'projects.edit-new/new-page }))

(secretary/defroute (str CONTEXT "/projects/:id") {id :id}
  (swap! state/client-state assoc :current-page
         {:component #'projects.show/page :id id}))

(secretary/defroute (str CONTEXT "/projects/:id/issues/:keys") {id :id encoded-ks :keys}
  (swap! state/client-state assoc :current-page
         {:component #'projects.show/issue
          :id id :issue-keys (->> encoded-ks js/decodeURIComponent
                                  (.parse js/JSON) js->clj (map keyword))}))

(secretary/defroute (str CONTEXT "/projects/:id/error/:keys") {id :id raw-keys :keys}
  (swap! state/client-state assoc :current-page
         {:component #'projects.show/issue :id id}))

(secretary/defroute (str CONTEXT "/projects/:id/edit") {id :id}
  (swap! state/client-state assoc :current-page
         {:component #'projects.edit-new/edit :id id}))

(secretary/defroute (str CONTEXT "/ui/debug") []
  (swap! state/client-state assoc :current-page
         {:component #'cider-ci.repository.ui.debug/page}))

(secretary/defroute (str CONTEXT "*") []
  (swap! state/client-state assoc :current-page
         {:component #'not-found}))

;; -------------------------
;; Initialize app

(defn mount-root []
  (reagent/render [current-page] (.getElementById js/document "app")))


(defn init! []
  (accountant/configure-navigation!
    {:nav-handler
     (fn [path]
       (secretary/dispatch! path))
     :path-exists?
     (fn [path]
       (secretary/locate-route path))})
  (accountant/dispatch-current!)
  (mount-root))
