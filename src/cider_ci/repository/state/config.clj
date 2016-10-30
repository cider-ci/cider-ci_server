; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.repository.state.config
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer [keyword str]])

  (:require

    [cider-ci.utils.config :refer [get-config]]
    [cider-ci.repository.state.db :as db]

    ))


(defn- update-config []
  (let [config (-> (get-config)
                   (select-keys [:server_base_url
                                 :repository_service_advanced_api_edit_fields]))]
    (swap! db/db assoc-in [:config] config)))


(defn initialize []
  (update-config))

