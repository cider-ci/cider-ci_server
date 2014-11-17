; Copyright (C) 2013, 2014 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.api.resources.trial-attachments
  (:require 
    [cider-ci.api.pagination :as pagination]
    [cider-ci.api.util :as util]
    [cider-ci.utils.debug :as debug]
    [cider-ci.utils.http :as http]
    [cider-ci.utils.rdbms :as rdbms]
    [clj-logging-config.log4j :as logging-config]
    [clojure.data.json :as json]
    [clojure.java.jdbc :as jdbc]
    [clojure.tools.logging :as logging]
    [compojure.core :as cpj]
    [ring.util.response :as response]
    [sqlingvo.core :as sqling]
    ) 
  (:refer-clojure :exclude [distinct group-by])
  (:use 
    [sqlingvo.core]
    ))



;### get-attachments ############################################################
(defn build-attachments-base-query  [trial-id]
  (select [:id :path]
          (from :trial_attachments)
          (where `(like :path ~(str "/" trial-id "%")))
          (order-by (asc :path))))

(defn attachments-data [trial-id query-params]
  (let [query (-> (build-attachments-base-query trial-id)
                  (pagination/add-offset query-params)
                  sql)
        _ (logging/debug query)]
    (jdbc/query (rdbms/get-ds) query)))

(defn get-attachments [request]
  (let [trial-id (-> request :route-params :trial_id util/uuid)
        query-params (-> request :query-params)]
    {:body 
     {:trial_attachment_ids 
      (map :id (attachments-data trial-id query-params))}}))


;### routes #####################################################################
(def routes 
  (cpj/routes
    (cpj/GET "/trial/:trial_id/trial-attachments/" request (get-attachments request))
    ))



;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
