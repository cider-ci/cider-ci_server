; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.server.repository.status-pushes.remotes.github
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer [keyword str]])
  (:require
    [cider-ci.server.repository.remote :as remote]
    [cider-ci.server.repository.status-pushes.shared :refer [db-update-state db-update-status-pushes db-update-state-to-waiting-if-idle]]
    [cider-ci.utils.config :as config :refer [get-config get-db-spec]]
    [cider-ci.utils.daemon :refer [defdaemon]]
    [cider-ci.utils.rdbms :as rdbms]
    [cider-ci.utils.row-events :as row-events]

    [clj-time.core :as time]
    [clj-http.client :as http-client]
    [clojure.data.json :as json]
    [clojure.java.jdbc :as jdbc]
    [honeysql.core :as sql]
    [logbug.catcher :as catcher]
    [pg-types.all]
    [ring.util.codec :refer [url-encode]]
    )
  (:require
    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher :refer [snatch]]
    [logbug.debug :as debug :refer [I> I>> identity-with-logging]]
    [logbug.thrown :as thrown]
    ))

;### POST Status ##############################################################

(defn build-target-url [params]
  (let [config (get-config)
        ui-http-config (-> config :services :ui :http)]
    (str (:server_base_url config)
         (:context ui-http-config)
         (:sub_context ui-http-config)
         "/workspace/jobs/"
         (:job_id params))))

(defn build-url [params]
  (str (remote/api-endpoint params)
       "/repos/" (remote/api-namespace params)
       "/" (remote/api-name params)
       "/statuses/"  (:commit_id params)))

(defn map-state [state]
  (case state
    ("pending" "dispatching" "executing") "pending"
    ("passed") "success"
    ("failed") "failure"
    "error"))

(defn build-body [params]
  {"state"  (map-state (:state params))
   "target_url"  (build-target-url params)
   "description" (str (:state params) " - " (:name params))
   "context" (str (:name-prefix params) " - " (:name params))})

(defn post-status [params]
  (future
    (let [id (:repository_id params)]
      (assert id)
      (db-update-state-to-waiting-if-idle id)
      (locking (remote/api-endpoint params)
        (catcher/snatch
          {:return-fn (fn [e]
                        (db-update-status-pushes
                          id (fn [status-pushes]
                               (assoc status-pushes
                                      :state "error"
                                      :last_error (str e)
                                      :last_error_at (time/now)))))}
          (let [token (:remote_api_token params)
                url (-> params build-url)
                body (-> params build-body json/write-str)]
            (db-update-state (:repository_id params) "posting")
            (logging/debug [token url body])
            (http-client/post url {:oauth-token token
                                   :body body
                                   :content-type :json })
            (db-update-status-pushes
              (:repository_id params)
              #(assoc % :state "ok" :last_posted_at (time/now)))))))))

;#### debug ###################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
