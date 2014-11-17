; Copyright (C) 2013, 2014 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.


(ns cider-ci.api.pagination
  (:require 
    [cider-ci.utils.debug :as debug]
    [cider-ci.utils.http :as http]
    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [compojure.core :as cpj]
    [sqlingvo.core :as sqlingvo]
    ))


(defn page-number [params]
  (let [page-string (:page params)]
    (if page-string (Integer/parseInt page-string)
      0)))

(defn compute-offset [params]
  (let [page (page-number params)]
    (* 10 page)))

(defn add-offset [query params]
  (let [off (compute-offset params)]
    (sqlingvo/compose query 
                      (sqlingvo/offset off) 
                      (sqlingvo/limit 10))))

(defn next-page-query-query-params [query-params]
  (let [i-page (page-number query-params)]
    (assoc query-params 
           :page (+ i-page 1))))


;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
