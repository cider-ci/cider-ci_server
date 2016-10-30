; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.repository.fetch-and-update.fetch
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer [keyword str]])
  (:require
    [cider-ci.repository.fetch-and-update.shared :refer :all]
    [cider-ci.utils.system :as system]
    [clj-time.core :as time]
    [me.raynes.fs :as fs]
    )
  (:require
    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug :refer [I> I>> identity-with-logging]]
    [logbug.thrown :as thrown]
    ))

(defn git-init [path]
  (system/exec! ["git" "init" "--bare" path]))

(defn- git-fetch [repository path]
  (system/exec!
    ["git" "fetch" (git-url repository) "--force" "--tags" "--prune"  "+*:*"]
    {:in "\n" :timeout "30 Minutes", :dir path, :env {"TERM" "VT-100"}}))

(defn- git-update-server-info [path]
  (system/exec!
    ["git" "update-server-info"]
    {:dir path :env {"TERM" "VT-100"}}))

(defn fetch [repository path]
  (let [id (:id repository)]
    (db-update-fetch-and-update id #(assoc % :state "fetching"))
    (Thread/sleep 1000)
    (when-not (fs/exists? path) (git-init path))
    (git-fetch repository path)
    (db-update-fetch-and-update id #(assoc % :state "ok" :last_fetched_at (time/now)))))



;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
