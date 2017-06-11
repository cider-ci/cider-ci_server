; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.server.state.config
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer [keyword str]])

  (:require
    [cider-ci.server.state.db :as db]

    [cider-ci.utils.config :refer [get-config]]
    ))


(defn- update-config []
  (let [config (-> (get-config)
                   (select-keys [:server_base_url
                                 :repository_service_advanced_api_edit_fields]))]
    (swap! db/db assoc-in [:config] config)))


(defn initialize []
  (update-config))

