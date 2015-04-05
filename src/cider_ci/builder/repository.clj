; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.builder.repository
  (:require 
    [cider-ci.builder.util :as util]
    [cider-ci.utils.config :as config :refer [get-config]]
    [cider-ci.utils.debug :as debug]
    [cider-ci.utils.http :as http]
    [cider-ci.utils.rdbms :as rdbms]
    [cider-ci.utils.with :as with]
    [clj-yaml.core :as yaml]
    [clojure.core.memoize :as memo]
    [clojure.java.jdbc :as jdbc]
    [clojure.tools.logging :as logging]
    ))


(defn- parse-path-content [path content]
  (with/log :warn (yaml/parse-string content)))

(defn- assert-path-spec
  "Raises an exception if path doesn't start with a '/'"
  [path]
  (if (re-find #"^\/" path) 
    path
    (throw (IllegalArgumentException. 
             (str "The string value of `_cider-ci_include` must start with a slash '/'. " )))))

(defn get-path-content_ [git-ref-id path]
  (let [url (http/build-service-url 
              :repository  
              (str "/path-content/" git-ref-id (assert-path-spec path)))
        res (try (with/log :warn (http/get url {})))
        body (:body res)]
    (parse-path-content path body)))


(def get-path-content (memo/lru get-path-content_
                                :lru/threshold 500))

;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)

