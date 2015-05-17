; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.builder.repository
  (:require 
    [cider-ci.builder.util :as util]
    [cider-ci.utils.config :as config :refer [get-config]]
    [drtom.logbug.debug :as debug]
    [cider-ci.utils.http :as http]
    [cider-ci.utils.rdbms :as rdbms]
    [drtom.logbug.catcher :as catcher]
    [clj-yaml.core :as yaml]
    [clojure.core.memoize :as memo]
    [clojure.java.jdbc :as jdbc]
    [clojure.tools.logging :as logging]
    [clojure.data.json :as json]
    ))


(defn- parse-path-content [path content]
  (catcher/wrap-with-log :warn (yaml/parse-string content)))

(defn get-path-content_ [git-ref-id path]
  (let [url (http/build-service-url 
              :repository  
              (str "/path-content/" git-ref-id "/" path))
        res (try (catcher/wrap-with-log :warn (http/get url {})))
        body (:body res)]
    (parse-path-content path body)))

(defn ls-tree [git-ref-id params]
  (logging/info {:params params})
  (let [url (http/build-service-url 
              :repository  
              (str "/ls-tree/" git-ref-id "/" )
              params)
        res (try (catcher/wrap-with-log :warn (http/get url {})))
        body (:body res)]
    (logging/info url)
    (json/read-str body :key-fn keyword)))


(def get-path-content (memo/lru get-path-content_
                                :lru/threshold 500))

;(def get-path-content get-path-content_)


;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)

