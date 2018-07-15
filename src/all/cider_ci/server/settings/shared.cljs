; Copyright © 2017 - 2017 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.server.settings.shared
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

    [cljs.pprint :refer [pprint]]
    [cljs.core.async :as async]
    [reagent.core :as reagent]
    [reagent.ratom :as ratom]
    ))


(def settings* (ratom/atom {}))

(def fetch-id* (ratom/atom nil))

(defn settings-breadcrumb-component
  [& {:keys [active?] :or {:active? false}}]
  (let [internal [:span "Settings"]]
    [:li {:key :settings
          :class (when active? "active")}
     (if active?
       internal
       [:a {:href (routes/settings-path {})} internal])]))

(defn welcome-page-breadcrumb-component
  [& {:keys [active?] :or {:active? false}}]
  (let [internal [:span "Welcome-Page"]]
    [:li {:key :welcome-page
          :class (when active? "active")}
     (if active?
       internal
       [:a {:href (routes/settings-section-path {:section "welcome-page"})}
        internal])]))

(defn fetch
  ([]
   (let [resp-chan (async/chan)
         id (request/send-off {:url "/cider-ci/settings/" :method :get}
                              {:modal false
                               :title "Fetch Settings"}
                              :chan resp-chan)]
     (reset! fetch-id* id)
     (go (let [resp (<! resp-chan)]
           (when (and (= (:status resp) 200) ;success
                      (= id @fetch-id*)) ;still the most recent request
             (reset! settings* (:body resp)))
           (js/setTimeout fetch (* 60 1000)))))))


;;; server-entity-event-update-receiver ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn server-entity-event-receiver [event]
  (case (:table_name event)
    "settings" (fetch)
    nil))

(defn debug-component []
  (when (:debug @state/client-state)
    [:div.debug
     [:hr]
     [:h2 "Page Debug"]
     [:div.settings
      [:h3 "settings*"]
      [:pre
       (with-out-str (pprint @settings*))]]
     ]))
