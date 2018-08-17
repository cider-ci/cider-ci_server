(ns cider-ci.utils.rdbms
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer [keyword str presence]])
  (:require
    [clojure.java.jdbc :as jdbc]
    [hikari-cp.core :as hikari]
    [pg-types.all]
    [ring.util.codec]

    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug :refer [I> I>> identity-with-logging]]
    [logbug.ring :refer [wrap-handler-with-logging]]
    [logbug.thrown :as thrown]
    )
  (:import
    [java.net.URI]
    [com.codahale.metrics MetricRegistry]
    ))

;;; status ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defonce metric-registry* (atom nil))

(defn Timer->map [t]
  {:count (.getCount t)
   :mean-reate (.getMeanRate t)
   :one-minute-rate (.getOneMinuteRate t)
   :five-minute-rate (.getFiveMinuteRate t)
   :fifteen-minute-rate (.getFifteenMinuteRate t)
   }) 

(defn status []
  {:gauges (->> 
             @metric-registry* .getGauges
             (map (fn [[n g]] [n (.getValue g)]))
             (into {}))
   :timers (->> @metric-registry* .getTimers
                (map (fn [[n t]] [n (Timer->map t)]))
                (into {}))})

(defn extend-pg-params [params]
  (assoc params
         :password (or (:password params)
                       (System/getenv "PGPASSWORD"))
         :username (or (:username params)
                       (System/getenv "PGUSER"))
         :port (or (:port params)
                   (System/getenv "PGPORT"))))

(defonce ds (atom nil))
(defn get-ds [] @ds)

(defn wrap-tx [handler]
  (fn [request]
    (jdbc/with-db-transaction [tx @ds]
      (try 
        (let [resp (handler (assoc request :tx tx))]
          (when-let [status (:status resp)]
            (when (>= status 400 )
              (logging/warn "Rolling back transaction because error status " status)
              (jdbc/db-set-rollback-only! tx)))
          resp)
        (catch Throwable th
          (logging/warn "Rolling back transaction because of " th)
          (jdbc/db-set-rollback-only! tx)
          (throw th)))
      ;TODO insert important request properties, tx id, and user id to
      ; `requests` for mutating requests
      )))

(defn create-ds [params]
  {:datasource
   (hikari/make-datasource
     {:auto-commit        true
      :read-only          false
      :connection-timeout 30000
      :validation-timeout 5000
      :idle-timeout       (* 1 60 1000) ; 1 minute
      :max-lifetime       (* 1 60 60 1000) ; 1 hour 
      :minimum-idle       (-> params :min-pool-size presence (or 3))
      :maximum-pool-size  (-> params :max-pool-size presence (or 16))
      :pool-name          "db-pool"
      :adapter            "postgresql"
      :username           (:username params)
      :password           (:password params)
      :database-name      (:database params)
      :server-name        (:host params)
      :port-number        (:port params)
      :register-mbeans    false
      :metric-registry (:metric-registry params)
      :health-check-registry (:health-check-registry params)})})

(defn close-ds [ds]
  (-> ds :datasource hikari/close-datasource))


(defn init [params health-check-registry]
  (reset! metric-registry* (MetricRegistry.))
  (when @ds
    (do
      (logging/info "Closing db pool ...")
      (close-ds @ds)
      (reset! ds nil)
      (logging/info "Closing db pool done.")))
  (logging/info "Initializing db pool " params " ..." )
  (reset! ds (create-ds (assoc params
                               :metric-registry @metric-registry*
                               :health-check-registry health-check-registry)))
  (logging/info "Initializing db pool done.")
  @ds)

;### Debug ####################################################################
;(debug/debug-ns *ns*)
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
