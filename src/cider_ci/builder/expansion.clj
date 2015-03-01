; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.builder.expansion
  (:require 
    [cider-ci.builder.repository :as repository]
    [cider-ci.builder.util :as util]
    [cider-ci.utils.debug :as debug]
    [cider-ci.utils.rdbms :as rdbms]
    [cider-ci.utils.with :as with]
    [clj-yaml.core :as yaml]
    [clojure.java.jdbc :as jdbc]
    [clojure.tools.logging :as logging]
    ))

;### expand ###############################################################

(defn- get-include-content-for-path [git-ref-id path]
  (let [content (repository/get-path-content 
                  git-ref-id path)]
    (if-not (map? content)
      (throw (IllegalStateException. 
               (str "Only maps can be included. Given " 
                    (type content))))
      content)))


(defn- get-include-content-for-seq-of-paths [git-ref-id seq-of-paths]
  (reduce 
    (fn [content include-path]
      (util/deep-merge content 
                       (get-include-content-for-path 
                         git-ref-id include-path)))
    {} seq-of-paths))


(defn- get-include-content [git-ref-id include-value]
  (cond 
    (string? include-value) (get-include-content-for-path 
                              git-ref-id include-value)
    (and (coll? include-value)
         (every? string? include-value)) (get-include-content-for-seq-of-paths  
                                           git-ref-id
                                           include-value)
    :else (throw (IllegalArgumentException. 
                   (str "I don't know how get and include " include-value)
                   ))))


;### expand ###############################################################


(defn expand 
  ([git-ref-id spec]
   (with/logging 
     (cond 
       (map? spec) (if-let [include-value (:_cider-ci_include spec)]
                     (let [content (get-include-content git-ref-id include-value)]
                       (expand git-ref-id (util/deep-merge (dissoc spec :_cider-ci_include)
                                                           content)))
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
       :else spec)))) 


;### Debug ####################################################################
;(debug/debug-ns *ns*)
;(debug/debug-ns 'cider-ci.utils.http)
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
