; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.server.api.resources.trials
  (:require
    [cider-ci.server.api.pagination :as pagination]
    [cider-ci.server.api.util :as util]

    [cider-ci.utils.http :as utils-http]
    [cider-ci.utils.rdbms :as rdbms]
    [cider-ci.utils.config :refer [get-config]]

    [clojure.data.json :as json]
    [clojure.java.jdbc :as jdbc]
    [compojure.core :as cpj]
    [compojure.handler :as cpj.handler]
    [honeysql.core :as hc]
    [honeysql.helpers :as hh]
    [ring.adapter.jetty :as jetty]
    [ring.middleware.cookies :as cookies]
    [ring.middleware.json]
    [ring.util.response :as response]

    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.debug :as debug]
    ))


(defonce conf (atom nil))

;### get-trials #################################################################

(defn build-trials-base-query [task-id]
  (-> (hh/from :trials)
      (hh/select :trials.id :trials.created_at)
      (hh/modifiers :distinct)
      (hh/where [:= :trials.task_id task-id])
      (hh/order-by [:trials.created_at :asc] [:trials.id :asc])))

(defn filter-by-state [query params]
  (if-let [state (:state params)]
    (-> query (hh/merge-where [:= :trials.state state]))
    query))

(defn trials-data [task-id query-params]
  (let [query (-> (build-trials-base-query task-id)
                  (filter-by-state query-params)
                  (pagination/add-offset-for-honeysql query-params)
                  hc/format)]
    (logging/debug {:query query})
    (jdbc/query (rdbms/get-ds) query)))

(defn get-trials  [request]
  {:body {:trials
          (trials-data (-> request :route-params :task_id)
                       (-> request :query-params))}})


;### retry ######################################################################

(defn retry [request]
  (let [task-id (-> request :route-params :task_id)
        user-id (-> request :authenticated-entity :id)
        _ (assert (= (-> request :authenticated-entity :type) :user))
        url (str (-> (get-config) :base-url :url)
                 "/dispatcher/tasks" task-id "/retry")
        _ (logging/info {:url url})
        body (json/write-str {:created_by user-id})
        params {:body body :throw-exceptions false}
        _ (logging/info {:params params})
        response (utils-http/request :post url params)]
    (if (map? (:body response))
      (select-keys response [:body :status])
      {:status (:status response)
       :body {:message (:body (-> response :body str))}})))


;### routes #####################################################################

(def routes
  (cpj/routes
    (cpj/GET "/tasks/:task_id/trials/" _ get-trials)
    (cpj/POST "/tasks/:task_id/trials/retry" _  retry)))


;### init #####################################################################

(defn initialize [new-conf]
  (reset! conf new-conf))


;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
