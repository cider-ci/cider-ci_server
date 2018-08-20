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


(defonce projects* core/projects*)

(defn init-project [project]
  (let [repository (repositories/init project)]
    (swap! projects* assoc 
           (:id project)
           (atom (assoc project 
                        :repository repository)))))

(defn de-init-project [project-bare]
  (when-let [project (some-> @projects* 
                             (get (:id project-bare))
                             deref)]
    (repositories/de-init project)
    (swap! projects* dissoc (:id project))))


;;;;;;;;;;

(def projects-chan* (atom nil))

(defn handle-project-event [event]
  (catcher/with-logging {}
    (logging/info 'handle-project-event event)
    (case (:operation event)
      "INSERT" (init-project (:data_new event))
      "DELETE" (de-init-project (:data_old event))
      "UPDATE" (cond (-> event 
                         :data_diff 
                         :repository_updated_at) (some-> 
                                                   @projects* 
                                                   (get (-> event :data_new :id))
                                                   deref 
                                                   git-sql/import-branches)
                     :else nil))))

;(async/<!! @projects-chan*)

(defn- init-subscribe-projects []
  (-> projects-chan*
      (reset! (async/chan (async/sliding-buffer 100)))
      (table-events/subscribe "projects"))
  (async/go-loop []
                 (when-let [event (async/<! @projects-chan*)]
                   (handle-project-event event)
                   (recur))))

;;;;;;;;;;

(defn init [ds]
  (let [db-projects (->> (-> (sql/select :projects.*)
                             (sql/from :projects)
                             sql/format)
                         (jdbc/query ds)
                         (map #(assoc % :project-id (:id %))))]
    (doseq [project db-projects]
      (init-project project)))
  (doseq [[_ project] @projects*]
    (git-sql/import-branches @project))
  (init-subscribe-projects))

(defn de-init []
  (when-let [c @projects-chan*]
    (async/close! c)
    (reset! projects-chan* nil)))

;(catcher/with-logging {} (some-> @projects* (get "test") git-sql/import-branches))

;#### debug ###################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns 'clojure.tools.cli)
;(debug/debug-ns *ns*)
