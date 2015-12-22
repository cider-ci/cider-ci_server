; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.dispatcher.dispatch
  (:require
    [cider-ci.dispatcher.dispatch.build-data :as build-data]
    [cider-ci.dispatcher.dispatch.next-trial :as next-trial]
    [cider-ci.dispatcher.executor :as executor-utils]
    [cider-ci.dispatcher.task :as task]
    [cider-ci.dispatcher.trials :as trials]
    [cider-ci.utils.config :refer [get-config]]
    [cider-ci.utils.daemon :refer [defdaemon]]
    [cider-ci.utils.http :as http]
    [cider-ci.utils.rdbms :as rdbms :refer [get-ds]]
    [clj-http.client :as http-client]
    [clj-logging-config.log4j :as logging-config]
    [clojure.data.json :as json]
    [clojure.java.jdbc :as jdbc]
    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug]
    ))

;### dispatch #################################################################

(defn- issues-count [trial]
  (-> (jdbc/query (get-ds)
                  ["SELECT count(*) FROM trial_issues WHERE trial_id = ? "
                   (:id trial)]) first :count))

(defn post-trial [url basic-auth-pw data]
  (catcher/wrap-with-log-warn
    (http-client/post url
                      {:content-type :json
                       :body (json/write-str data)
                       :insecure? true
                       :basic-auth ["dispatcher" basic-auth-pw ]})))

(defn- get-entity-or-throw [table-name id]
  (or (first (jdbc/query
               (get-ds) [(str "SELECT * FROM " table-name " WHERE id = ?") id]))
      (throw (ex-info "Entity not found." {:table-name table-name :id id}))))

(defn dispatch [trial-id executor-id]
  (catcher/wrap-with-suppress-and-log-error
    (let [trial (get-entity-or-throw "trials" trial-id)
          executor (get-entity-or-throw "executors" executor-id)]
      (try (trials/wrap-trial-with-issue-and-throw-again
             trial  "Error during dispatch"
             (let [data (build-data/build-dispatch-data trial executor)
                   url (str (:base_url executor)  "/execute")
                   basic-auth-pw (executor-utils/http-basic-password executor)]
               (trials/update-trial {:id (:id trial)
                                     :state "dispatching"
                                     :executor_id (:id executor)})
               (post-trial url basic-auth-pw data)
               (trials/update-trial {:id (:id trial) :state "executing"})))
           (catch Exception e
             (let  [row (if (<= 3 (issues-count trial))
                          {:state "failed"
                           :warnings ["Too many issues, giving up to dispatch this trial."]
                           :executor_id nil}
                          {:state "pending" :executor_id nil})]
               (trials/update-trial (conj trial row))
               false))))))

(defn dispatch-trials []
  (loop []
    (when-let [{trial-id :id executor-id :executor_id}
               (next-trial/next-trial-with-executor-for-push)]
      (jdbc/update! (get-ds) :trials
                    {:state "dispatching"
                     :executor_id executor-id}
                    ["id = ?" trial-id])
      (future (dispatch trial-id executor-id))
      (recur))))


;#### dispatch service ########################################################

(defdaemon "dispatch-service" 0.3 (dispatch-trials))


;### initialize ##############################################################
(defn initialize []
  (start-dispatch-service)
  )

;#### debug ###################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns 'cider-ci.utils.http)
;(debug/debug-ns *ns*)

