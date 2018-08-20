; Copyright © 2013 - 2018 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.
 
(ns cider-ci.server.projects.repositories.shared
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer [keyword str presence]])
  (:require
    [cider-ci.utils.git-gpg :as git-gpg]
    [cider-ci.utils.honeysql :as sql]
    [cider-ci.utils.system :as system]
    [cider-ci.utils.nio :as nio]

    [clojure.java.jdbc :as jdbc]
    [compojure.core :as cpj]

    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug])
  (:import 
    [org.eclipse.jgit.storage.file FileRepositoryBuilder]
    [java.nio.file Files FileSystems]
    [java.io File]
    ))


(def ^:dynamic repositories-dir-path
  (nio/path
    (System/getProperty "user.dir")
    "data"
    "repositories"))

(defn path [project-params]
  (let [project-id (or (:project-id project-params)
                       (:id project-params))]
    (assert (presence project-id))
    (nio/path repositories-dir-path project-id)))

(defn file-repository [path]
  (.build (doto (new FileRepositoryBuilder)
            (.setGitDir (.toFile path))
            (.setBare))))

;(path {:project-id "test"})

;(nio/rmdir-recursive (path {:project-id "test"}))


;#### debug ###################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns 'clojure.tools.cli)
;(debug/debug-ns *ns*)
