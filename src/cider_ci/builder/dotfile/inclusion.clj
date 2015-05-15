; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.builder.dotfile.inclusion
  (:require 
    [cider-ci.builder.repository :as repository]
    [cider-ci.builder.util :as util]
    [drtom.logbug.debug :as debug]
    [cider-ci.utils.rdbms :as rdbms]
    [drtom.logbug.catcher :as catcher]
    [clj-yaml.core :as yaml]
    [clojure.java.jdbc :as jdbc]
    [clojure.tools.logging :as logging]
    ))

;### include ##############################################################

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


;### include ##############################################################


(defn include
  ([git-ref-id spec]
   (catcher/wrap-with-log-warn
     (cond 
       (map? spec) (if-let [include-value (:_cider-ci_include spec)]
                     (let [content (get-include-content git-ref-id include-value)]
                       (include git-ref-id (util/deep-merge (dissoc spec :_cider-ci_include)
                                                           content)))
                     (into {} (map (fn [pair]
                                     (let [[k v] pair]
                                       [k (include git-ref-id v)]))
                                   spec)))
       (coll? spec) (map (fn [spec-item]
                           (logging/debug {:spec-item spec-item})
                           (cond 
                             (map? spec-item) (include git-ref-id spec-item)
                             (coll? spec-item) (include git-ref-id spec-item)
                             :else spec-item))
                         spec)
       :else spec)))) 


;### Debug ####################################################################
;(debug/debug-ns *ns*)
;(debug/debug-ns 'cider-ci.utils.http)
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
