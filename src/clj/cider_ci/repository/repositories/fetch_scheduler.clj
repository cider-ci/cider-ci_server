; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.repository.repositories.fetch-scheduler
  (:require
    [cider-ci.repository.web.edn]
    [cider-ci.repository.state :as state]
    [cider-ci.repository.repositories.fetch-and-update :as fetch-and-update]

    [cider-ci.utils.rdbms :as rdbms]
    [cider-ci.utils.core :refer :all]
    [cider-ci.utils.daemon :as daemon :refer [defdaemon]]
    [cider-ci.utils.duration :as duration]


    [clj-time.core :as time]
    [clojure.java.jdbc :as jdbc]

    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher :refer [snatch]]
    [logbug.debug :as debug]
    [logbug.thrown :as thrown]
    ))


;### update repository ########################################################

(defn fetch-and-update [id]
  (logging/warn 'fetch-and-update "does not do anything yet")
  )

(defn- git-fetch-and-update-interval [repository]
  (time/seconds
    (snatch
      {:return-expr 60}
      (duration/parse-string-to-seconds
        (:remote_fetch_interval repository)))))

(defn last-succeeded-or-failed-fetch-at [repository]
  "Returns the timestamp of the most resent (succeeded or failed) fetch or nil."
  (let [{last-fetched-at :last_fetched_at
         last-fetch-failed-at :last_fetch_failed_at} repository]
    (cond (and last-fetched-at
               last-fetch-failed-at) (if (time/after? last-fetched-at last-fetch-failed-at)
                                       last-fetched-at last-fetch-failed-at)
          :else (or last-fetched-at last-fetch-failed-at))))

(defn- git-fetch-is-due? [repository]
  (if-let [last-fetched-at (last-succeeded-or-failed-fetch-at repository)]
    (time/after?
      (time/now)
      (time/plus last-fetched-at (git-fetch-and-update-interval repository)))
    true))

(defn fetch-and-update-repositories []
  (doseq [ [_ repository] (:repositories @state/db)]
    (when (git-fetch-is-due? repository)
      (fetch-and-update/fetch-and-update repository))))


(defdaemon "fetch-and-update-repositories" 1 (fetch-and-update-repositories))


;### initialize ###############################################################

(defn initialize []
  (start-fetch-and-update-repositories))


;#### debug ###################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
