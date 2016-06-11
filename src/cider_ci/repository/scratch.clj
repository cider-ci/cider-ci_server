; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.


(ns cider-ci.repository.repositories
  (:require
    [cider-ci.repository.repositories.fetch-and-update :as fetch-and-update]
    [cider-ci.repository.branches :as branches]
    [cider-ci.repository.git.repositories :as git.repositories]
    [cider-ci.repository.sql.branches :as sql.branches]
    [cider-ci.utils.fs :as ci-fs]
    [cider-ci.utils.rdbms :as rdbms]
    [cider-ci.utils.system :as system]
    [clj-logging-config.log4j :as logging-config]
    [clj-time.core :as time]
    [clojure.java.jdbc :as jdbc]
    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug]
    [logbug.thrown :as thrown]
    [me.raynes.fs :as fs]
    ))


(catcher/snatch {}
  (jdbc/query (rdbms/get-ds) ["SELECT 1 + )"])
  )

