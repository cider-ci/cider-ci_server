; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.server.projects.repositories.project-configuration.shared
  (:require
    ;[cider-ci.server.repository.git.repositories :as git.repositories]
    ;[cider-ci.server.repository.sql.repository :as sql.repository]
    [cider-ci.server.projects.repositories.core :as repositories.core]
    [cider-ci.server.projects.core :as projects.core]
    [cider-ci.utils.rdbms :as rdbms]

    [yaml.core :as yaml]
    [clojure.data.json :as json]
    [clojure.java.jdbc :as jdbc]
    [honeysql.core :as sql]

    [logbug.debug :as debug]
    [logbug.catcher :as catcher]
    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
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
         (jdbc/query (rdbms/get-ds))
         (map :id))))

(defn resolve-tree-id-for-commit-ids [commit-ids]
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

(defn resolve-submodule-git-ref [git-refs paths]
  (let [commit-ids (get-commit-ids git-refs)]
    (when-not (seq commit-ids)
      (throw (ex-info (str "The commit for " git-refs " was not found."
                           " Is a git push pending?")
                      {:status 404})))
    (loop [commit-ids commit-ids
           paths paths]
      (if-let [path (first paths)]
        (recur (get-commit-refs-for-submodule commit-ids path) (rest paths))
        (first commit-ids)))))


;##############################################################################

(defn parse-path-content [path content]
  (catcher/with-logging {}
    (let [path (clojure.string/lower-case path)]
      (try
        (cond
          (re-matches #".*(yml|yaml)" path) (yaml/parse-string content)
          (re-matches #".*json" path) (json/read-str content :key-fn keyword)
          :else (throw (ex-info "Project Configuration Parse Error"
                                {:status 422
                                 :title "Project Configuration Parse Error"
                                 :description
                                 (str "Only YAML and JSON documents are allowed. "
                                      "The project configuration requested to parse "
                                      path)})))
        (catch Exception e
          (throw (ex-info "Project Configuration Parse Error"
                          {:status 422
                           :title "Project Configuration Parse Error"
                           :description
                           (str "Parser error for file " path ". \n"
                                "The original error message is: "
                                (.getMessage e))})))))))

(defn find-repo-for-id! [git-id]
  (or (projects.core/resolve-project git-id)
      (throw (ex-info (str git-id " could not be resolved, is a git push pending?")
                      {:status 404}))))

(defn project-file-missing-exception [repository path git-ref]
  (ex-info "Project Configuration Error - File Missing"
           {:status 404
            :title "Project Configuration Error - File Missing"
            :description (str "The file for the path `" path "`"
                              " was not found in the project *" (:name repository)"*"
                              " for the git reference `" git-ref"`.")}))

(defn get-content [git-ref path submodule-path]
  (assert (not (clojure.string/blank? path)))
  (assert (let [submodule-path submodule-path]
            (and (coll? submodule-path)
                 (not (map? submodule-path))
                 (every? string? submodule-path))))
  (let [submodule-ref (resolve-submodule-git-ref [git-ref] submodule-path)]
    (let [project (projects.core/resolve-project submodule-ref)]
      (when-not project
        (throw (ex-info {:status 404
                         :title "The git ref could not be resolved to any repository."
                         :hint "Is a repository push (in a submodule) pending?"
                         :ref git-ref})))
      (or (some-> (repositories.core/path-content 
                    (:repository project) submodule-ref path)
                  String.)
          (throw
            (ex-info "Project Configuration Error - File Missing"
                     {:status 404
                      :title "Project Configuration Error - File Missing"
                      :description (str "The file for the path `" path "`"
                                        " was not found in the project *" (:name project)"*"
                                        " for the git reference `" git-ref"`.")}))))))

;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
