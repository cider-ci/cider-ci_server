; Copyright © 2017 - 2017 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.server.settings.ui
  (:refer-clojure :exclude [str keyword])
  (:require-macros
    [reagent.ratom :as ratom :refer [reaction]]
    [cljs.core.async.macros :refer [go]]
    )
  (:require
    [cider-ci.server.settings.welcome-page :as welcome-page]
    [cider-ci.constants :as constants :refer [GRID-COLS]]
    [cider-ci.server.client.breadcrumbs :as breadcrumbs]
    [cider-ci.server.client.connection.request :as request]
    [cider-ci.server.client.constants :refer [CONTEXT]]
    [cider-ci.server.client.routes :as routes]
    [cider-ci.server.client.shared :refer [pre-component]]
    [cider-ci.server.client.state :as state]
    [cider-ci.server.settings.shared :as settings]
    [cider-ci.utils.core :refer [keyword str presence]]

    [cljs.core.async :as async]
    [reagent.core :as reagent]
    [reagent.ratom :as ratom]
    ))


(defn page-component []
  [:div.settings-page
   (breadcrumbs/breadcrumb-component
     [(breadcrumbs/home-li-component)
      (settings/settings-breadcrumb-component :active? true) ]
     [(settings/welcome-page-breadcrumb-component)])
   [:h1 "Settings"]
   [settings/debug-component]
   ])

(defn page-dispatch []
  [:div.settings
   (case (-> @state/page-state :current-page :section)
     :welcome-page [welcome-page/page]
     [page-component])])


(defn post-mount-setup [component]
  (swap! state/page-state assoc
         :server-entity-event-receiver settings/server-entity-event-receiver)
  (settings/fetch))

(defn page-will-unmount [& args]
  (swap! state/page-state dissoc :server-entity-event-receiver))

(defn page []
  (reagent/create-class
    {:component-did-mount post-mount-setup
     :reagent-render page-dispatch
     :component-will-unmount page-will-unmount}))
