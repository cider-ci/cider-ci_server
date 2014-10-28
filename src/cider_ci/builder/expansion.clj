; Copyright (C) 2013, 2014 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.builder.expansion
  (:require 
    [cider-ci.builder.util :as util]
    [cider-ci.utils.debug :as debug]
    [cider-ci.utils.with :as with]
    [cider-ci.utils.http :as http]
    [cider-ci.utils.rdbms :as rdbms]
    [clj-yaml.core :as yaml]
    [clojure.java.jdbc :as jdbc]
    [clojure.tools.logging :as logging]
    ))


(defonce ^:private conf (atom {}))

;### expand ###############################################################

(defn parse-path-content [path content]
  ; TODO handle json content
  (try 
    (with/logging (yaml/parse-string content))
    (catch Exception _
      (throw (IllegalStateException. 
               (str "Failed to parse the content of " path ))))))

(defn get-path-content [git-ref-id path]
  (let [url (http/build-url (:repository_service @conf) 
                            (str "/path-content/" git-ref-id  "/" path))
        res (try (with/logging (http/get url {}))
                 (catch Exception _ 
                   (throw 
                     (IllegalStateException. 
                       (str "Failed to retrieve the contents of " path ". ")))))
        body (:body res)]
    (parse-path-content path body)))

(defn expand [git-ref-id spec]
  (with/logging 
  (cond 
    (map? spec) (if-let [path (:_cider-ci_include spec)]
                  (let [content (get-path-content git-ref-id path)]
                    (if-not (map? content)
                      (throw (IllegalStateException. (str "Only maps can be included. Given " (type content) )))
                      (expand git-ref-id (util/deep-merge (dissoc spec :_cider-ci_include)
                                                          content))))
                  (into {} (map (fn [pair]
                                  (let [[k v] pair]
                                    [k (expand git-ref-id v)]))
                                spec)))
    (coll? spec) (map (fn [spec-item]
                       (logging/debug {:spec-item spec-item})
                       (cond 
                         (map? spec-item) (expand git-ref-id spec-item)
                         (coll? spec-item) (expand git-ref-id spec-item)
                         :else spec-item))
                     spec)
    :else spec))) 


;### initialize ###############################################################

(defn initialize [new-conf] 
  (reset! conf new-conf)
  (http/initialize new-conf))


;### Debug ####################################################################
;(debug/debug-ns *ns*)
;(debug/debug-ns 'cider-ci.utils.http)
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
