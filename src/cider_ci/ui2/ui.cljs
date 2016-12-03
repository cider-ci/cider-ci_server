(ns cider-ci.ui2.ui
  (:require
    [cider-ci.ui2.constants :refer [CONTEXT]]
    [cider-ci.ui2.ui.debug :as debug]
    [cider-ci.ui2.ui.state :as state]
    [cider-ci.ui2.create-admin.ui]
    [cider-ci.ui2.welcome-page.ui]
    [cider-ci.ui2.session.ui]

    [reagent.core :as reagent]
    [secretary.core :as secretary :include-macros true]
    [accountant.core :as accountant]

    [cljsjs.jquery]
    [cljsjs.bootstrap]
    ))

(defn general-debug-section []
  (when (:debug @state/client-state)
    [:section.debug
     [:hr]
     [:h1 "Debug"]
     [:div.client-state
      [:h2 "Client State"]
      [:pre (.stringify js/JSON (clj->js @state/client-state) nil 2)]]
     [:div.page-state
      [:h2 "Page State"]
      [:pre (.stringify js/JSON (clj->js @state/page-state) nil 2)]]
     ]))

(defn current-page []
  (let [location-href (-> js/window .-location .-href)
        location-url (goog.Uri. location-href)]
    (swap! state/client-state assoc
           :current-url location-href
           :current-path (.getPath location-url))
    [:div.container-fluid
     ;[:h1 "TEST"]
     [:div.page [(-> @state/page-state :current-page :component)]]
     [general-debug-section]
     ]))


(defn root-page []
  [:h1 "Hello Cider-CI UI2"]
  )

;--- Routes

(secretary/defroute (str CONTEXT "/debug") []
  (swap! state/page-state assoc :current-page
         {:component #'debug/page}))

(secretary/defroute (str CONTEXT "/initial-admin") []
  (swap! state/page-state assoc :current-page
         {:component #'root-page}))


;--- Initialize

(defn mount-root []
  (when-let [app (.getElementById js/document "app")]
    (reagent/render [current-page] app)))

(defn init! []
  (when-let [app (.getElementById js/document "app")]
    (accountant/configure-navigation!
      {:nav-handler (fn [path] (secretary/dispatch! path))
       :path-exists?  (fn [path] (secretary/locate-route path))})
    (accountant/dispatch-current!)
    (mount-root)))
