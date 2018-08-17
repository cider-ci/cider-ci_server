; Copyright © 2013 - 2018 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.
 
(ns cider-ci.server.projects
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer [keyword str presence]])
  (:require
    [cider-ci.server.paths :refer [path]]
    [cider-ci.server.projects.core :as core]
    [cider-ci.server.projects.repositories :as repositories]
    [cider-ci.server.projects.repositories.git-sql :as git-sql]
    [cider-ci.server.utils.table-events :as table-events]
    [cider-ci.utils.git-gpg :as git-gpg]
    [cider-ci.utils.honeysql :as sql]
    [cider-ci.utils.rdbms :as ds]

    [clojure.java.jdbc :as jdbc]
    [clojure.core.async :as async]
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

(defn process-project-update [event]
  (logging/info 'process-project-update event))

(defn project-listener-chan [project]
  (let [c (async/chan (async/sliding-buffer 1))]
    (table-events/subscribe c "projects")
    (async/go (while true (process-project-update (-> (async/<! c)))))
    c))

(defn init-project [project]
  (let [repository (repositories/init project)
        listener-chan (project-listener-chan project)]
    (swap! projects* assoc 
           (:id project)
           (assoc project 
                  :repository repository
                  :listener-chan listener-chan))))


;;;;;;;;;;

(def projects-chan* (atom nil))

(defn handle-project-event [event]
  (logging/info 'handle-project-event event))

(defn- init-subscribe-projects []
  (-> projects-chan*
      (reset! (async/chan (async/sliding-buffer 100)))
      (table-events/subscribe "projects"))
  (async/go-loop []
                 (when-let [event (async/<! @projects-chan*)]
                   (handle-project-event event)
                   (recur))))

;;;;;;;;;;

(defn init 
  ([] (init {}))
  ([{tx :tx :or {tx @ds/ds}}]
   (init-subscribe-projects)
   (let [db-projects (->> (-> (sql/select :projects.*)
                              (sql/from :projects)
                              sql/format)
                          (jdbc/query tx)
                          (map #(assoc % :project-id (:id %))))]
     (doseq [project db-projects]
       (init-project project)))
   (doseq [[_ project] @projects*]
     (git-sql/import-branches project))))

(defn de-init []
  (when-let [c @projects-chan*]
    (async/close! c)
    (reset! projects-chan* nil)))

;(catcher/with-logging {} (some-> @projects* (get "test") git-sql/import-branches))
