; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.builder.dotfile.inclusion
  (:require 
    [cider-ci.builder.dotfile.task-generation :as task-generation]
    [cider-ci.builder.repository :as repository]
    [cider-ci.builder.util :as util :refer [deep-merge]]
    [drtom.logbug.debug :as debug]
    [cider-ci.utils.rdbms :as rdbms]
    [drtom.logbug.catcher :as catcher]
    [clj-yaml.core :as yaml]
    [clojure.java.jdbc :as jdbc]
    [clojure.tools.logging :as logging]
    [honeysql.core :as hc]
    [honeysql.helpers :as hh]
    ))


;#### git helpers #############################################################

(defn- get-commit-ids [git-refs]
  (let [query (-> (hh/select :commits.id)
                  (hh/from :commits)
                  (hh/modifiers :distinct)
                  (hh/merge-join :branches_commits [:= 
                                                    :commits.id 
                                                    :branches_commits.commit_id])
                  (hh/merge-where [:or 
                                   [:in :id git-refs]
                                   [:in :tree_id git-refs]])
                  (hc/format))]
    (->> query
         (jdbc/query  (rdbms/get-ds))
         (map :id))))

(defn- resolve-tree-id-for-commit-ids [commit-ids]
  (let [query (-> (hh/select :commits.tree_id :commits.committer_date)
                  (hh/from :commits)
                  (hh/merge-where [:in :commits.id commit-ids])
                  (hh/merge-join :branches_commits [:= 
                                                    :commits.id 
                                                    :branches_commits.commit_id])
                  (hh/limit 1)
                  (hh/order-by [:commits.committer_date :desc])
                  (hc/format))]
    (->> query
         (jdbc/query  (rdbms/get-ds))
         (map :tree_id)
         first)))

(defn- get-commit-refs-for-submodule [commit-ids path]
  (let [query (-> (hh/select :submodules.submodule_commit_id)
                  (hh/from  :submodules)
                  (hh/merge-join :branches_commits [:= 
                                                    :submodules.submodule_commit_id  
                                                    :branches_commits.commit_id])
                  (hh/merge-where [:in :submodules.commit_id commit-ids])
                  (hh/merge-where [:= :submodules.path path])
                  (hc/format))]
    (->> query
         (jdbc/query  (rdbms/get-ds))
         (map :submodule_commit_id))))

(defn- resolve-submodule-git-ref [git-refs paths]
  (let [commit-ids (get-commit-ids git-refs)]
    (loop [commit-ids commit-ids
           paths paths]
      (if-let [path (first paths)]
        (recur (get-commit-refs-for-submodule commit-ids path) (rest paths))
        (resolve-tree-id-for-commit-ids commit-ids))))) 


;##############################################################################

(defn- get-include-content-for-path [git-ref-id path]
  (assert git-ref-id)
  (assert path)
  (let [content (repository/get-path-content git-ref-id path)]
    (if-not (map? content)
      (throw (IllegalStateException. 
               (str "Only maps can be included. Given " 
                    (type content))))
      content)))


;##############################################################################

(defn format-include-spec [include-spec]
  (cond 
    (= (type include-spec) java.lang.String) {:path include-spec
                                              :submodule []}
    (map? include-spec) {:submodule (or (:submodule include-spec) [])
                         :path (or (:path include-spec) 
                                   (throw (IllegalStateException. 
                                            (str "Can not determine :path for include-spec: "
                                                 include-spec))))}
    :else (throw (IllegalStateException. (str "include-spec must be either a map or string, is " 
                                              (type include-spec))))))

(defn- format-include-specs [include-specs]
  (cond 
    (= (type include-specs) java.lang.String) [(format-include-spec include-specs)]
    (coll? include-specs) (map format-include-spec include-specs)))


;##############################################################################

(declare include)
(defn- get-inclusion [git-ref-id include-spec]
  (let [submodule-ref (resolve-submodule-git-ref [git-ref-id] 
                                                 (or (:submodule include-spec) []))
        content (get-include-content-for-path submodule-ref (:path include-spec))]
    (include submodule-ref content)))

(defn- get-inclusions [git-ref-id include-specs]
  (->> (format-include-specs include-specs)
       (map #(get-inclusion git-ref-id %))
       (reduce deep-merge)))


;##############################################################################

(defn include-map [git-ref-id spec]
  (if-let [include-specs (:_cider-ci_include spec)]
    (let [included (get-inclusions git-ref-id include-specs)]
      (deep-merge 
        (dissoc spec :_cider-ci_include)
        included))
    (->> spec
         (map (fn [[k v]] [k (include git-ref-id v)]))
         (into {}))))

(defn include [git-ref-id spec]
  (catcher/wrap-with-log-warn
    (cond 
      (map? spec) (->> spec
                       (include-map git-ref-id)
                       (#(if (:_cider-ci_generate-tasks %)
                           (task-generation/generate-tasks git-ref-id %)
                           %)))
      (coll? spec) (->> spec
                        (map #(if (coll? %) 
                                (include git-ref-id %)
                                %)))
      :else spec)))


;### Debug ####################################################################
;(debug/debug-ns 'cider-ci.utils.http)
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
