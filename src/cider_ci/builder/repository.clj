; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.builder.repository
  (:require
    [cider-ci.builder.util :as util]
    [cider-ci.utils.config :as config :refer [get-config]]
    [logbug.debug :as debug]
    [cider-ci.utils.http :as http]
    [cider-ci.utils.rdbms :as rdbms]
    [logbug.catcher :as catcher]
    [clj-yaml.core :as yaml]
    [clojure.core.memoize :as memo]
    [clojure.java.jdbc :as jdbc]
    [clojure.tools.logging :as logging]
    [clojure.data.json :as json]
    ))


(defn- parse-path-content [path content]
  (catcher/with-logging {}
    (let [path (clojure.string/lower-case path)]
      (cond
        (re-matches #".*(yml|yaml)" path) (yaml/parse-string content)
        (re-matches #".*json" path) (json/read-str content :key-fn keyword)
        :else (throw (IllegalArgumentException. (str "Parsing " path " is not supported.")))))))

(defn- get-path-content_unmemoized [git-ref-id path]
  (let [url (http/build-service-url
              :repository
              (str "/path-content/" git-ref-id "/" path))
        res (http/get url {})
        body (:body res)]
    (parse-path-content path body)))

(def get-path-content
  (memo/lru get-path-content_unmemoized :lru/threshold 500))

; disable caching (temporarily)
; (def get-path-content get-path-content_unmemoized)


;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)

