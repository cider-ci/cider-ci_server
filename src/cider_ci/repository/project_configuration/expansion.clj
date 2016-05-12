; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.repository.project-configuration.expansion
  (:require
    [cider-ci.repository.project-configuration.task-generation :as task-generation]
    [cider-ci.repository.project-configuration.shared :refer [get-content]]

    [cider-ci.utils.core :refer [deep-merge]]
    [cider-ci.utils.rdbms :as rdbms]

    [clj-yaml.core :as yaml]
    [clojure.java.jdbc :as jdbc]
    [honeysql.core :as sql]

    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug]
    ))


;#### git helpers #############################################################

(defn- get-commit-ids [git-refs]
  (let [query (-> (sql/select :commits.id)
                  (sql/from :commits)
                  (sql/modifiers :distinct)
                  (sql/merge-join :branches_commits [:=
                                                    :commits.id
                                                    :branches_commits.commit_id])
                  (sql/merge-where [:or
                                   [:in :id git-refs]
                                   [:in :tree_id git-refs]])
                  (sql/format))]
    (->> query
         (jdbc/query  (rdbms/get-ds))
         (map :id))))

(defn- resolve-tree-id-for-commit-ids [commit-ids]
  (let [query (-> (sql/select :commits.tree_id :commits.committer_date)
                  (sql/from :commits)
                  (sql/merge-where [:in :commits.id commit-ids])
                  (sql/merge-join :branches_commits [:=
                                                    :commits.id
                                                    :branches_commits.commit_id])
                  (sql/limit 1)
                  (sql/order-by [:commits.committer_date :desc])
                  (sql/format))]
    (->> query
         (jdbc/query  (rdbms/get-ds))
         (map :tree_id)
         first)))

(defn- get-commit-refs-for-submodule [commit-ids path]
  (let [query (-> (sql/select :submodules.submodule_commit_id)
                  (sql/from  :submodules)
                  (sql/merge-join :branches_commits [:=
                                                    :submodules.submodule_commit_id
                                                    :branches_commits.commit_id])
                  (sql/merge-where [:in :submodules.commit_id commit-ids])
                  (sql/merge-where [:= :submodules.path path])
                  (sql/format))
        res (->> query
                 (jdbc/query  (rdbms/get-ds))
                 (map :submodule_commit_id))]
    (if (seq res)
      res
      (let [message "Project Configuration Error - Submodule could not be resolved"]
        (throw
          (ex-info
            message
            {:status 404
             :title message
             :description
             (str "The commit id for submodule path `" path
                  "` and the commit(s) " (clojure.string/join ", " (seq commit-ids))
                  " could not be resolved!  \n\n A git push might be pending or"
                  " a submodule might not be configured as a repository in"
                  " your Cider-CI server.")}))))))

(defn- resolve-submodule-git-ref [git-refs paths]
  (let [commit-ids (get-commit-ids git-refs)]
    (when-not (seq commit-ids)
      (throw (ex-info (str "the commit for " git-refs " was not found,"
                           " is a git push pending?")
                      {:status 404})))
    (loop [commit-ids commit-ids
           paths paths]
      (if-let [path (first paths)]
        (recur (get-commit-refs-for-submodule commit-ids path) (rest paths))
        (resolve-tree-id-for-commit-ids commit-ids)))))


;##############################################################################

(defn- get-include-content-for-path [git-ref-id path]
  (assert git-ref-id)
  (assert path)
  (let [content (get-content git-ref-id path)]
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

(declare expand)

(defn- get-inclusion [git-ref-id include-spec]
  (let [submodule-ref (resolve-submodule-git-ref [git-ref-id]
                                                 (or (:submodule include-spec) []))
        content (get-include-content-for-path submodule-ref (:path include-spec))]
    (expand submodule-ref content)))

(defn- get-inclusions [git-ref-id include-specs]
  (->> (format-include-specs include-specs)
       (map #(get-inclusion git-ref-id %))
       (reduce deep-merge)))


;##############################################################################

(defn include-map [git-ref-id spec]
  (if-let [include-specs (:include spec)]
    (let [included (get-inclusions git-ref-id include-specs)]
      (include-map git-ref-id
                   (deep-merge
                     (dissoc spec :include)
                     included)))
    (->> spec
         (map (fn [[k v]] [k (expand git-ref-id v)]))
         (into {}))))

(defn expand [git-ref-id spec]
  (catcher/with-logging {}
    (cond
      (map? spec) (->> spec
                       (include-map git-ref-id)
                       (#(if (:generate_tasks %)
                           (task-generation/generate-tasks git-ref-id %)
                           %)))
      (coll? spec) (->> spec
                        (map #(if (coll? %)
                                (expand git-ref-id %)
                                %)))
      :else spec)))


;### Debug ####################################################################
;(debug/debug-ns 'cider-ci.utils.http)
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
