; Copyright © 2013 - 2018 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.
 
(ns cider-ci.server.projects.repositories
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer [keyword str presence]])
  (:require
    [cider-ci.server.projects.repositories.shared :as shared]
    [cider-ci.server.projects.repositories.http-backend :as http-backend]

    [cider-ci.utils.system :as system]
    [cider-ci.utils.nio :as nio]

    [clojure.java.jdbc :as jdbc]
    [compojure.core :as cpj]

    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug])
  (:import 
    [java.nio.file Files FileSystems]
    [org.eclipse.jgit.storage.file FileRepositoryBuilder]
    [java.io File InputStreamReader DataInputStream]
    [java.lang Process ProcessBuilder]
    ))


(def path shared/path)

(defn init [project]
  (let [path (path project)]
    (when-not (nio/dir? path)
      (system/exec! ["git" "init" "--bare" (.toString path)]))
    (let [repository (.build (doto (new FileRepositoryBuilder)
                               (.setGitDir (.toFile path))
                               (.setBare)))]
      (assert (.getRef repository "HEAD"))
      repository)))

(defn de-init [project]
  (let [path (path project)]
    (when (nio/dir? path)
      (nio/rmdir-recursive path))))

(def http-handler http-backend/http-handler)

;#### debug ###################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns 'cider-ci.utils.shutdown)
;(debug/debug-ns *ns*)
