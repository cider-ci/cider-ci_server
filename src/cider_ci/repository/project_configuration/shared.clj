; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.repository.project-configuration.shared
  (:require
    [cider-ci.repository.git.repositories :as git.repositories]
    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.debug :as debug]
    [logbug.catcher :as catcher]
    [clj-yaml.core :as yaml]
    [clojure.data.json :as json]
    [cider-ci.repository.sql.repository :as sql.repository]
    ))

(defn- parse-path-content [repository path id content]
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
                           (str "Parser error for file " path
                                " of " id " in " (:name repository)". \n"
                                "The original error message is: "
                                (.getMessage e))})))))))

(defn find-repo-for-id! [id]
  (or (sql.repository/resolve id)
      (throw (ex-info (str id " could not be resolved, is a git push pending?")
                      {:status 404}))))

(defn get-content
  ([id path]
   (get-content (find-repo-for-id! id) id path))
  ([repository id path]
   (try (->> (git.repositories/get-path-contents repository id path)
             (parse-path-content repository path id))
        (catch clojure.lang.ExceptionInfo e
          (cond
            (re-matches #"(?is).*does not exist in.*"
                        (or (-> e ex-data :err) ""))
            (throw (ex-info "Project Configuration Error - File Missing"
                            {:status 404
                             :title "Project Configuration Error - File Missing"
                             :description (str "The file for the path `" path "`"
                                               " was not found in the project *" (:name repository)"*"
                                               " for the id `" id "`.")}))
            :else (throw e))))))


;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
