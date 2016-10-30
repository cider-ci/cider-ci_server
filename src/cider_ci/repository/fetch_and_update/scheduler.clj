; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.repository.fetch-and-update.scheduler
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer [keyword str]])

  (:require
    [cider-ci.repository.fetch-and-update.shared :refer [fetch-and-update db-get-fetch-and-update]]
    [cider-ci.repository.web.edn]
    [cider-ci.repository.state :as state]

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

(defn- git-fetch-and-update-interval [repository]
  (time/seconds
    (snatch
      {:return-expr 60}
      (duration/parse-string-to-seconds
        (:remote_fetch_interval repository)))))

(defn last-succeeded-or-failed-fetch-at [repository]
  "Returns the timestamp of the most resent (succeeded or failed) fetch or nil."
  (let [{last-fetched-at :last_fetched_at
         last-fetch-failed-at :last_fetch_and_update_failed_at} repository]
    (if (and last-fetched-at last-fetch-failed-at)
      (if (time/after? last-fetched-at last-fetch-failed-at)
        last-fetched-at last-fetch-failed-at)
      (or last-fetched-at last-fetch-failed-at))))

(defn- due? [repository]
  (let [fetch-and-update (db-get-fetch-and-update (:id repository))
        last-fetched-at (:last_fetched_at fetch-and-update)
        last-error-at (:last_error_at fetch-and-update)
        reference (if (and last-fetched-at last-error-at)
                    (time/latest last-fetched-at last-error-at)
                    (or last-fetched-at last-error-at))]
    (cond (contains? ["fetching" "waiting"]
                     (:state fetch-and-update)) false
          (:pending? fetch-and-update) false
          (not reference) true
          :else (time/after?
                  (time/now)
                  (time/plus reference
                             (git-fetch-and-update-interval repository))))))

(defn fetch-and-update-repositories []
  (doseq [[_ repository] (:repositories (state/get-db))]
    (when (due? repository)
      (fetch-and-update repository))))

(defdaemon "fetch-and-update-repositories" 1 (fetch-and-update-repositories))

;### initialize ###############################################################

(defn initialize []
  (start-fetch-and-update-repositories))


;#### debug ###################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
