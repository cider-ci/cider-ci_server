; Copyright (C) 2013, 2014 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.api.resources.trials
  (:require 
    [cider-ci.utils.debug :as debug]
    [cider-ci.utils.http :as http]
    [cider-ci.utils.http-server :as http-server]
    [cider-ci.utils.rdbms :as rdbms]
    [clj-http.client :as http-client]
    [clj-logging-config.log4j :as logging-config]
    [clojure.data.json :as json]
    [clojure.java.jdbc :as jdbc]
    [clojure.tools.logging :as logging]
    [compojure.core :as cpj]
    [compojure.handler :as cpj.handler]
    [ring.adapter.jetty :as jetty]
    [ring.middleware.cookies :as cookies]
    [ring.middleware.json]
    [ring.util.response :as response]
    [sqlingvo.core :as sqling]
    ) 
  (:refer-clojure :exclude [distinct group-by])
  (:use 
    [cider-ci.api.resources.shared :exclude [initialize]]
    [sqlingvo.core]
    ))


(defonce conf (atom nil))

;### get-trials #################################################################
(defn build-trials-base-query [task-id]
  (select (distinct [:trials.id :trials.updated_at])
          (from :trials)
          (where `(= :task_id ~(uuid task-id)) :and)
          (order-by (desc :trials.updated_at))
          (limit 10)))

(defn filter-by-state [query params]
  (if-let [state (:state params)]
    (compose query (where `(= :trials.state ~state) :and))
    query))

(defn trials-data [task-id query-params]
  (let [query (-> (build-trials-base-query task-id)
                  (filter-by-state query-params)
                  (add-offset query-params)
                  sql)
        trial-ids (map :id (jdbc/query (rdbms/get-ds) query))]
    {:_links (conj {:self {:href (str (trials-path task-id) "?"
                                      (http/build-url-query-string query-params))}
                    :cici:trial (map trial-link trial-ids)}
                   (next-and-previous-link-map (trials-path task-id)
                                               query-params (seq trial-ids))
                   (task-link-map task-id)
                   (curies-link-map)
                   )}))

(defn get-trials  [request]
  {:hal_json_data (trials-data (-> request :route-params :task_id)
                               (-> request :query-params))})


;### routes #####################################################################
(def routes 
  (cpj/routes
    (cpj/GET "/task/:task_id/trials" request (get-trials request))
    ))


;### init #####################################################################
(defn initialize [new-conf]
  (reset! conf new-conf))



;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
