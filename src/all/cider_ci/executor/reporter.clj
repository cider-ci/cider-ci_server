(ns cider-ci.executor.reporter
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer :all])
  (:require
    [cider-ci.utils.config :as config :refer [get-config]]
    [cider-ci.executor.json]
    [cider-ci.utils.http :as http]
    [cider-ci.utils.core :refer :all]
    [cider-ci.utils.duration :as duration]

    [clj-time.core :as time]
    [clj-time.format :as time-format]
    [clojure.data.json :as json]
    [clojure.string :as string]

    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher]
    [logbug.thrown :as thrown]
    [clj-logging-config.log4j :as logging-config]
    [logbug.debug :as debug]
    ))


;### Read config ##############################################################

(defn- validate-max-retries [v]
  (if-not (and (number? v) (<= 0 v) (> 25 v))
    (throw (ex-info "Max retries value does not meet constraints"  {:value v}))
    v))

(defn- max-retries []
  (catcher/snatch {:return-expr 10}
    (-> (get-config)
        :reporter
        :max-retries
        validate-max-retries)))

(defn- validate-pause-duration [p]
  (if-not (and (number? p) (< 0 p) (> 1000 p))
    (throw (ex-info "Pause duration doesn't meet constraints"  {:value p}))
    p))

(defn- retry-factor-pause-duration []
  (catcher/snatch {:return-expr 1}
    (-> (get-config)
        :reporter
        :retry-factor-pause-duration
        duration/parse-string-to-seconds
        validate-pause-duration)))


;### Send #####################################################################

(defn- send-request [method url params]
  (http/request method url params))

(defn send-request-with-retries
  ([method url params]
   (send-request-with-retries method url params (max-retries)))
  ([method url params max-retries]
   (loop [retry 0]
     (Thread/sleep (* retry retry (retry-factor-pause-duration) 1000))
     (let [res  (try
                  (send-request method url params)
                  {:url url :method method :params params :send-status "success"}
                  (catch Exception e
                    (logging/warn "failed " (inc retry) " time to PATCH to "
                                  url " with error: " (thrown/stringify e))
                    {:url url :method method :params params, :send-status "failed" :error e}))]
       (if (= (:send-status res) "success")
         res
         (if (>= retry max-retries)
           res
           (recur (inc retry))))))))


;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;
;(logging-config/set-logger! :level :debug)
;(debug/wrap-with-log-debug #'patch-as-json)
;
;(debug/debug-ns *ns*)
