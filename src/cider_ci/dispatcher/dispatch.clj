; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.dispatcher.dispatch
  (:require
    [cider-ci.dispatcher.dispatch.build-data :as build-data]
    [cider-ci.dispatcher.dispatch.next-trial :as next-trial]
    [cider-ci.dispatcher.executor :as executor-utils]
    [cider-ci.dispatcher.task :as task]
    [cider-ci.dispatcher.trial :as trial-utils]
    [cider-ci.utils.config :refer [get-config]]
    [cider-ci.utils.daemon :as daemon]
    [cider-ci.utils.http :as http]
    [cider-ci.utils.rdbms :as rdbms]
    [clj-http.client :as http-client]
    [clj-logging-config.log4j :as logging-config]
    [clojure.data.json :as json]
    [clojure.java.jdbc :as jdbc]
    [clojure.tools.logging :as logging]
    [drtom.logbug.catcher :as catcher]
    [drtom.logbug.debug :as debug]
    ))

;### dispatch #################################################################

(defn- issues-count [trial]
  (-> (jdbc/query (rdbms/get-ds)
                  ["SELECT count(*) FROM trial_issues WHERE trial_id = ? " (:id trial)] )
      first :count))

(defn post-trial [url basic-auth-pw data]
  (catcher/wrap-with-log-warn
    (http-client/post url
                      {:content-type :json
                       :body (json/write-str data)
                       :insecure? true
                       :basic-auth ["dispatcher" basic-auth-pw ]})))

(defn dispatch [trial executor]
  (try
    (trial-utils/wrap-trial-with-issue-and-throw-again
      trial  "Error during dispatch"
      (let [data (build-data/build-dispatch-data trial executor)
            url (str (:base_url executor)  "/execute")
            basic-auth-pw (executor-utils/http-basic-password executor)]
        ; TODO the following seems to be not necessary, remove?
        (jdbc/update! (rdbms/get-ds) :trials
                      {:state "dispatching" :executor_id (:id executor)}
                      ["id = ?" (:id trial)])
        (post-trial url basic-auth-pw data)))
    (catch Exception e
      (let  [row (if (<= 3 (issues-count trial))
                   {:state "failed" :error "Too many issues, giving up to dispatch this trial "
                    :executor_id nil}
                   {:state "pending" :executor_id nil})]
        (trial-utils/update (conj trial row))
        false))))


(defn dispatch-trials []
  (when-let [next-trial  (next-trial/get-next-trial-to-be-dispatched)]
    (loop [trial next-trial
           executor (next-trial/choose-executor-to-dispatch-to trial)]
      (jdbc/update! (rdbms/get-ds) :trials
                    {:state "dispatching"
                     :executor_id (:id executor)}
                    ["id = ?" (:id trial)])
      (future (dispatch trial executor))
      (when-let [trial (next-trial/get-next-trial-to-be-dispatched)]
        (recur trial (next-trial/choose-executor-to-dispatch-to trial))))))


;#### dispatch service ########################################################

(defn get-dispatch-interval []
  (try
    (catcher/wrap-with-log-warn
      (or (-> (get-config) :services :dispatcher :dispatch_interval)
          1.0))
    (catch Exception _
      1.0)))

(daemon/define "dispatch-service"
  start-dispatch-service
  stop-dispatch-service
  (get-dispatch-interval)
  (logging/debug "dispatch-service")
  (dispatch-trials))


;### initialize ##############################################################
(defn initialize []
  (start-dispatch-service))

;#### debug ###################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns 'cider-ci.utils.http)
;(debug/debug-ns *ns*)

