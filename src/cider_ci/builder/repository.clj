; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.builder.repository
  (:require
    [cider-ci.builder.util :as util]
    [cider-ci.utils.config :as config :refer [get-config]]
    [cider-ci.utils.http :as http]
    [cider-ci.utils.rdbms :as rdbms]

    [clj-yaml.core :as yaml]
    [clojure.core.memoize :as memo]
    [clojure.java.jdbc :as jdbc]

    [clojure.data.json :as json]
    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug]
    ))


(defn- parse-path-content [path content]
  (catcher/with-logging {}
    (let [path (clojure.string/lower-case path)]
      (cond
        (re-matches #".*(yml|yaml)" path) (yaml/parse-string content)
        (re-matches #".*json" path) (json/read-str content :key-fn keyword)
        :else (throw (IllegalArgumentException. (str "Parsing " path " is not supported.")))))))

(defn- get-path-content_unmemoized [git-ref-id path]
  (let [url (str (:server_base_url (get-config))
                 "/cider-ci/repositories"
                 "/path-content/" git-ref-id "/" path)
        res (http/get url {})
        body (:body res)]
    (parse-path-content path body)))

(def get-path-content
  (memo/lru get-path-content_unmemoized :lru/threshold 128))

; disable caching (temporarily)
; (def get-path-content get-path-content_unmemoized)


;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)

