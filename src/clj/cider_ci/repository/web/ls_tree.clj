; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.repository.web.ls-tree

  (:require

    [cider-ci.repository.project-configuration.shared :refer [resolve-submodule-git-ref]]
    [cider-ci.repository.git.repositories :as git.repositories]
    [cider-ci.repository.sql.repository :as sql.repository]
    [cider-ci.repository.web.shared :refer :all]

    [clojure.data.json :as json]

    [ring.util.response :refer [charset]]

    [logbug.catcher :as catcher]
    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.debug :as debug :refer [I> I>>]]
    [logbug.thrown :as thrown]

    ))


;##### ls-tree ################################################################

(defn ls-tree [request]
  (->
    (try
      (catcher/with-logging
        {:message-fn (fn [e]
                       (str {:params (-> request :params)
                             :error (logbug.thrown/stringify e)}))}
        (let [git-ref (-> request :params :git_ref json/read-str)
              submodule (-> request :params :submodule json/read-str)
              resolved-git-ref (resolve-submodule-git-ref [git-ref] submodule)
              repository (sql.repository/resolve resolved-git-ref)]
          (when-not resolved-git-ref
            (throw (ex-info (str "The git-ref " git-ref " for the submodule "
                                 submodule " could not be resolved. "
                                 "Is a git push pending?") {:status 404})))
          (when-not repository
            (throw (ex-info "Repository not found." {:status 404})))
          (let [file-list (git.repositories/ls-tree
                            repository resolved-git-ref
                            (-> request :params :include_match json/read-str)
                            (-> request :params :exclude_match json/read-str))]
            {:body (json/write-str file-list)
             :headers {"Content-Type" "application/json"}})))
      (catch clojure.lang.ExceptionInfo e
        (case (-> e ex-data :status )
          404 {:status 404
               :headers {"Content-Type" "application/json"}
               :body (json/write-str (ex-data e)) }
          422 {:status 422
               :body (thrown/stringify e)}
          (respond-with-500 request e)))
      (catch Throwable e
        (respond-with-500 request e)))
    (charset "UTF-8")))



