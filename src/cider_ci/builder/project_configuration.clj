; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.builder.project-configuration
  (:require
    [clojure.data.json :as json]
    [cider-ci.utils.http :as http]
    [clj-logging-config.log4j :as logging-config]
    [clojure.core.memoize :as memo]
    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug]
    [clojure.data.json :as json]
    ))

(defn- convert-http-exception [e]
  (def ^:dynamic *last-http-exception* e)
  (logging/debug e)
  (catcher/snatch
    {:return-fn (fn [e] (throw e))}
    (let [data (-> e
                   ex-data
                   :body
                   (json/read-str :key-fn keyword))]
      (throw (ex-info (:title data) data)))))

(defn- get-project-configuration_unmemoized [tree-id]
  (let [url (http/build-service-url
              :repository
              (str "/project-configuration/" tree-id))]
    (catcher/snatch
      {:return-fn convert-http-exception}
      (-> url (http/get {:socket-timeout 3000
                         :conn-timeout 3000
                         :as :json})
          :body))))

(def get-project-configuration (memo/lru #(get-project-configuration_unmemoized %)
                                         :lru/threshold 500))

; disable caching (temporarily)
;(def get-project-configuration get-project-configuration_unmemoized)


;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
;(debug/debug-ns 'cider-ci.utils.http)
