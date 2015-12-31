; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.api.util
  (:require
    [cider-ci.utils.config :as config :refer [get-config]]
    [clj-http.client :as http-client]

    [clojure.tools.logging :as logging]
    [clj-logging-config.log4j :as logging-config]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug]
    ))

(defn sort-map [m]
  (into {} (sort m)))

(defn sort-map-recursive [m]
  (->> m
       (clojure.walk/prewalk
         (fn [el]
           (if (map? el)
             (sort-map el)
             el)
           ))))

(defn do-http-request [method url params]
  (logging/debug [method url params])
  (let [basic-auth (:basic_auth (get-config))]
    (catcher/with-logging {}
      (logging/debug  {:method method :url url :basic-auth basic-auth})
      (http-client/request
        (conj {:basic-auth [(:username basic-auth) (:password basic-auth)]
               :url url
               :method method
               :insecure? true
               :content-type :json
               :accept :json
               :socket-timeout 1000
               :conn-timeout 1000
               :as :auto}
              params)))))


;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
