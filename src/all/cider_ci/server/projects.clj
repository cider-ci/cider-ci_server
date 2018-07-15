; Copyright © 2013 - 2018 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.
 
(ns cider-ci.server.projects
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer [keyword str presence]])
  (:require
    [cider-ci.server.paths :refer [path]]
    [cider-ci.server.projects.repositories :as repositories]
    [cider-ci.server.projects.repositories.git-sql :as git-sql]
    [cider-ci.server.projects.core :as core]

    [cider-ci.utils.rdbms :as ds]

    [cider-ci.utils.git-gpg :as git-gpg]
    [cider-ci.utils.honeysql :as sql]

    [clojure.java.jdbc :as jdbc]
    [compojure.core :as cpj]

    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug]
    )
  (:import 
    [org.eclipse.jgit.api Git]
    [org.eclipse.jgit.revwalk RevCommit RevWalk]
    [org.eclipse.jgit.storage.file FileRepositoryBuilder]
    [java.io File]))


(def projects* core/projects*)

(defn init-project [project]
  (let [repository (repositories/init project)]
    (swap! projects* assoc 
           (:id project)
           (assoc project :repository repository))))

(defn init 
  ([] (init {}))
  ([{tx :tx :or {tx @ds/ds}}]
   (let [db-projects (->> (-> (sql/select :projects.*)
                              (sql/from :projects)
                              sql/format)
                          (jdbc/query tx)
                          (map #(assoc % :project-id (:id %))))]
     (doseq [project db-projects]
       (init-project project)))
   (doseq [[_ project] @projects*]
     (git-sql/import-branches project))))


;(catcher/with-logging {} (some-> @projects* (get "test") git-sql/import-branches))
