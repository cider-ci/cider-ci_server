(ns cider-ci.repository.core
  (:require
    [cider-ci.repository.constants :refer [CONTEXT]]
    [cider-ci.repository.state :as state]
    [cider-ci.repository.components.projects.edit-new :as projects.edit-new]
    [cider-ci.repository.components.projects.show :as projects.show]
    [cider-ci.repository.components.projects.index :as projects.index]
    [cider-ci.repository.components.navbar :as navbar]

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
     [:div.client-db
      [:h2 "Client State"]
      [:pre (with-out-str (pprint @state/client-state))]]
     [:div.server-db
      [:h2 "Server State"]
      [:pre (pr-str @state/server-state)]]]))

(defn sync-alert []
  [:div#sync-alert
   (when-not (:server-state-is-in-sync @state/client-state)
     [:div.alert.alert-danger
      [:strong "Bummer! "]
      "It seems that the state in your browser and the state
      on the server got out of sync. "
      [:strong "Please reload this page at your convenience manually!"]])])

(defn current-page []
  [:div.container-fluid
   [navbar/navbar]
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
   [general-debug-section]])

;; -------------------------
;; Routes


(secretary/defroute (str CONTEXT "/projects/") [query-params]
  ;(js/console.log qp1)
  ;(js/console.log query-params)
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

(secretary/defroute (str CONTEXT "/projects/:id/issues/:key") {id :id issue-key :key}
  (swap! state/client-state assoc :current-page
         {:component #'projects.show/issue :id id :issue-key issue-key }))

(secretary/defroute (str CONTEXT "/projects/:id/edit") {id :id}
  (swap! state/client-state assoc :current-page
         {:component #'projects.edit-new/edit :id id}))

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
