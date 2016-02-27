; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.dispatcher.dispatch.timeout-sweeper
  (:require
    [cider-ci.dispatcher.trials :as trials]
    [cider-ci.utils.config :as config :refer [get-config]]
    [cider-ci.utils.daemon :as daemon :refer [defdaemon]]
    [cider-ci.utils.rdbms :as rdbms :refer [get-ds]]
    [clojure.java.jdbc :as jdbc]
    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug]
    [honeysql.sql :refer :all]
    ))


(defn ^:private dispatch-timeout-query []
  (when-let [trial_dispatch_timeout (:trial_dispatch_timeout (get-config))]
    (-> (sql-select :id)
        (sql-from :trial)
        (sql-merge-where [:= :state "pending"])
        (sql-merge-where (sql-raw  (str "(created_at < (now() - interval '" trial_dispatch_timeout "'))")))
        (sql-format))))


(defn ^:private sweep-in-dispatch-timeout []
  (catcher/snatch {}
    (doseq [id (->> (dispatch-timeout-query)
                    (jdbc/query (get-ds))
                    (map :id))]
      (catcher/snatch {}
        (trials/update-trial {:id id :state "aborted" :error ["dispatch timeout"]})))))


(defdaemon "sweep-in-dispatch-timeout" 10 sweep-in-dispatch-timeout)

(defn initialize []
  (start-sweep-in-dispatch-timeout))

