(ns cider-ci.client.main
  (:refer-clojure :exclude [str keyword])
  (:require-macros
    [reagent.ratom :as ratom :refer [reaction]]
    )
  (:require
    [cider-ci.utils.core :refer [keyword str presence]]

    [cider-ci.client.request :as request]
    [cider-ci.client.routes :as routes]
    [cider-ci.client.state :as state]
    [cider-ci.client.ws :as ws]
    [cider-ci.repository.ui]
    [cider-ci.ui2.commits.ui]
    [cider-ci.ui2.constants :refer [CONTEXT]]
    [cider-ci.ui2.session.password.ui]
    [cider-ci.ui2.ui.debug :as debug]
    [cider-ci.ui2.ui.navbar]
    [cider-ci.ui2.ui.root]
    [cider-ci.ui2.welcome-page.ui]

    [cider-ci.executors.ui.create]
    [cider-ci.executors.ui.edit]
    [cider-ci.executors.ui.index]
    [cider-ci.executors.ui.show]

    [cider-ci.users.api-tokens.ui.create]
    [cider-ci.users.api-tokens.ui.edit]
    [cider-ci.users.api-tokens.ui.index]
    [cider-ci.users.api-tokens.ui.show]

    [cider-ci.create-initial-admin.ui]

    [clojure.string :as str]
    [fipp.edn :refer [pprint]]
    [reagent.core :as reagent]
    [secretary.core :as secretary :include-macros true]
    [accountant.core :as accountant]

    [cljsjs.jquery]
    [cljsjs.bootstrap]
    ))

(def components
  {
   "cider-ci.create-initial-admin.ui/page" cider-ci.create-initial-admin.ui/page
   "cider-ci.executors.ui.create/page" cider-ci.executors.ui.create/page
   "cider-ci.executors.ui.edit/page" cider-ci.executors.ui.edit/page
   "cider-ci.executors.ui.index/page" cider-ci.executors.ui.index/page
   "cider-ci.executors.ui.show/page" cider-ci.executors.ui.show/page
   "cider-ci.ui2.commits.ui/page" cider-ci.ui2.commits.ui/page
   "cider-ci.users.api-tokens.ui.create/page" cider-ci.users.api-tokens.ui.create/page
   "cider-ci.users.api-tokens.ui.edit/page" cider-ci.users.api-tokens.ui.edit/page
   "cider-ci.users.api-tokens.ui.index/page" cider-ci.users.api-tokens.ui.index/page
   "cider-ci.users.api-tokens.ui.show/page" cider-ci.users.api-tokens.ui.show/page
   })

(def user* (reaction (and (= (-> @state/server-state :user :type) "user")
                          (:user @state/server-state))))

(def authentication-providers*
  (reaction (:authentication_providers @state/server-state)))

(def current-url* (reaction (:current-url @state/client-state)))

(defn general-debug-section []
  (when (:debug @state/client-state)
    [:section.debug
     [:hr]
     [:h1 "Debug"]
     [:div.page-state
      [:h2 "Page State"]
      ;[:pre (with-out-str (pprint @state/page-state))]
      [:pre (.stringify js/JSON (clj->js @state/page-state) nil 2)]
      ]
     [:div.client-state
      [:h2 "Client State"]
      [:pre (with-out-str (pprint @state/client-state))]
      [:pre (.stringify js/JSON (clj->js @state/client-state) nil 2)]
      ]
     [:div.server-state
      [:h2 "Server State"]
      ;[:pre (with-out-str (pprint @state/server-state))]
      [:pre (.stringify js/JSON (clj->js @state/server-state) nil 2)]
      ]
     ]))


(defn not-found-page []
  [:h1.text-warning "404 Page Not Found!"]
  )

(defn current-page []
  (let [location-href (-> js/window .-location .-href)
        location-url (goog.Uri. location-href)]
    (swap! state/client-state assoc
           :current-url location-href
           :current-path (.getPath location-url))
    [:div.container-fluid
     [request/modal]
     [:div.page
      (let [component (-> @state/page-state :current-page :component)]
        (let [resolved-component (or (if (string? component)
                                       (get components component)
                                       component)
                                     not-found-page)]
          [resolved-component]))]
     [general-debug-section]]))

;--- Initialize

(defn mount []
  (when-let [app (.getElementById js/document "app")]
    (reagent/render [current-page] app))
  (when-let [nav-container (.getElementById js/document "nav")]
    (reagent/render [cider-ci.ui2.ui.navbar/navbar
                     user* current-url* authentication-providers*]
                    nav-container)))

(defn init! []
  (when-let [app (.getElementById js/document "app")]
    (accountant/configure-navigation!
      {:nav-handler (fn [path]
                      ;(js/console.log (clj->js ['nav-handler path]))
                      (swap! state/client-state assoc-in [:current-page :full-path] path)
                      (secretary/dispatch! path))
       :path-exists?  (fn [path] (secretary/locate-route path))})
    (accountant/dispatch-current!)
    (mount)
    (state/init)
    (when @user*
      (ws/init))))
