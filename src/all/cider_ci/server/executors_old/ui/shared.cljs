; Copyright © 2013 - 2017 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.server.executors-old.ui.shared
  (:refer-clojure :exclude [str keyword])
  (:require-macros
    [reagent.ratom :as ratom :refer [reaction]]
    )
  (:require
    [accountant.core :as accountant]
    [cider-ci.server.client.connection.request :as request]
    [cider-ci.server.client.routes :as routes]
    [cider-ci.server.client.state :as state]
    [cider-ci.server.client.utils :refer [humanize-datetime humanize-datetime-component]]
    [cider-ci.utils.core :refer [keyword str presence]]
    [reagent.core :as reagent]
    ))



(defn reload-executors [& {:keys [callback]}]
  (request/send-off
    {:url (routes/executors-path)
     :method :get}
    {:title "Fetch Executors"
     :autoremove-delay 0}
    :callback (fn [resp]
                (swap! state/client-state
                       assoc-in [:executors]
                       (->> resp :body :executors
                            (map (fn [[k v]] [k (assoc v :key k)]))
                            (into {})))
                (when callback
                  (callback resp)))))



(defn executor-not-found-component [executor-id*]
  [:div.text-warning
   [:h1 "404 Executor Not Found!"]
   [:p "We did not find an executor with the id "
    [:span {:style {:font-family :monospace}} @executor-id*] "." ]
   [:p "You can wait or try to reload the page if you think this is not correct."]])
