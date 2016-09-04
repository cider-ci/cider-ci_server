; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.repository.notifications.github
  (:require

    [cider-ci.utils.config :as config :refer [get-config get-db-spec]]
    [cider-ci.utils.daemon :refer [defdaemon]]
    [cider-ci.utils.rdbms :as rdbms]
    [cider-ci.utils.row-events :as row-events]

    [clj-http.client :as http-client]
    [clojure.data.json :as json]
    [clojure.java.jdbc :as jdbc]
    [honeysql.core :as sql]
    [logbug.catcher :as catcher]
    [pg-types.all]
    [ring.util.codec :refer [url-encode]]

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
  (->> [ "repos"
        (:remote_api_namespace params)
        (:remote_api_name params)
        "statuses"
        (:commit_id params)]
       (map url-encode)
       (concat [(:remote_api_endpoint params)])
       (clojure.string/join "/")))

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
   "context"  (str "Cider-CI@" (:hostname (get-config)) " - " (:name params) )})

(defn post-status [params]
  (catcher/snatch
    {}
    (let [token (:remote_api_token params)
          url (-> params build-url)
          body (-> params build-body json/write-str)]
      (logging/debug [token url body])
      (http-client/post url
                        {:oauth-token token
                         :body body
                         :content-type :json })
      [url body])))

;#### debug ###################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
