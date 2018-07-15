(ns cider-ci.utils.rdbms
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer [keyword str presence]])
  (:require
    [logbug.catcher :as catcher]
    [clojure.java.jdbc :as jdbc]
    [clojure.tools.logging :as logging]
    [ring.util.codec]
    [pg-types.all]

    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug :refer [I> I>> identity-with-logging]]
    [logbug.ring :refer [wrap-handler-with-logging]]
    [logbug.thrown :as thrown]
    )
  (:import
    [java.net.URI]
    [com.mchange.v2.c3p0 ComboPooledDataSource DataSources]
    ))


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


(defn check-connection []
  (if-not @ds
    {:OK? true :message "RDBMS is not initialized!"}
    (catcher/snatch
      {:return-fn (fn [e] {:OK? false :error (.getMessage e)})}
      (assert (->> (jdbc/query @ds
                               ["SELECT true AS state FROM schema_migrations LIMIT 1"])
                   first :state))
      (let [c3p0ds (-> @ds :datasource)
            max (.getMaxPoolSize c3p0ds)
            conns (.getNumConnectionsDefaultUser c3p0ds)
            busy (.getNumBusyConnectionsDefaultUser c3p0ds)
            idle (.getNumIdleConnectionsDefaultUser c3p0ds)
            usage (double (/ busy conns))]
        {:OK? true
         :Max max
         :Allocated conns
         :usage (Double/parseDouble (String/format "%.2f" (into-array [usage])))
         }))))

(defn reset []
  (logging/info "resetting c3p0 datasource")
  (when @ds (.hardReset (:datasource @ds)))
  (reset! ds nil))

(defn- get-url [db-conf]
  (str "jdbc:"
       (or (:url db-conf)
           (str (:subprotocol db-conf) ":" (:subname db-conf)))))

(defn- get-url-param [db-conf name]
  (when-let [url-string (:url db-conf)]
    (when-let [query (-> url-string java.net.URI/create .getQuery)]
      (-> query ring.util.codec/form-decode (get name)))))

;(get-url-param {:url "postgresql://localhost:5432/madek-v3_development?user=thomas"} "user")


(defn- get-user [db-conf]
  "Retrieves first non nil value of :user of db-conf,
  :username db-conf, user parameter of the url, PGUSER from env or nil"
  (or (:user db-conf)
      (:username db-conf)
      (get-url-param db-conf "user")
      (System/getenv "PGUSER")))

(defn- get-password [db-conf]
  "Retrieves first non nil value of :password of db-conf,
  password parameter of the url, PGPASSWORD from env or nil"
  (or (:password db-conf)
      (get-url-param db-conf "password")
      (System/getenv "PGPASSWORD")))

(defn- get-max-pool-size [db-conf]
  (when-let [ps (or (:max_pool_size db-conf)
                    (get-url-param db-conf "max-pool-size")
                    (get-url-param db-conf "max_pool_size")
                    (get-url-param db-conf "pool"))]
    (Integer. ps)))

(defn- create-c3p0-datasources [db-conf]
  (logging/info create-c3p0-datasources [db-conf])
  (reset! ds
          {:datasource
           (doto (ComboPooledDataSource.)
             (.setJdbcUrl (get-url db-conf))
             (#(when-let [user (get-user db-conf)]
                 (.setUser % user)))
             (#(when-let [password (get-password db-conf)]
                 (.setPassword % password)))
             (.setMaxPoolSize (or (get-max-pool-size db-conf) 10))
             (.setMinPoolSize (or (:min-pool-size db-conf) 3))
             (.setMaxConnectionAge
               (or (:max-connection-age db-conf) (* 3 60 60)))
             (.setMaxIdleTimeExcessConnections
               (or (:max-idle-time-exess-connections db-conf) (* 10 60)))
             )}))

(defn initialize [db-conf]
  (logging/info initialize [db-conf])
  (create-c3p0-datasources db-conf)
  (.addShutdownHook (Runtime/getRuntime)
                    (Thread. (fn []
                               (reset)))))

;(initialize {:subprotocol "sqlite" :subname ":memory:"})

(defn initialize2 [params]
  (when @ds
    (do
      (logging/info "Closing db pool ...")
      (-> @ds :datasource .close)
      (reset! ds nil)
      (logging/info "Closing db pool done.")))
  (logging/info "Initializing db pool " params " ..." )
  (let [url (str "jdbc:postgresql://"
                 (:host params)
                 (when-let [port (-> params :port presence)] (str ":" port))
                 "/" (:database params))]
    (logging/info {:url url})
    (reset!
      ds
      {:datasource
       (doto (ComboPooledDataSource.)
         (.setJdbcUrl url)
         (#(when-let [user (:username params)] (.setUser % user)))
         (#(when-let [password (:password params)] (.setPassword % password)))
         (.setMaxPoolSize (or (:max-pool-ѕize params) 50))
         (.setMinPoolSize 3)
         (.setMaxConnectionAge (* 3 60 60))
         (.setMaxIdleTimeExcessConnections (* 10 60)))}))
  (logging/info "Initializing db pool done.")
  @ds)



;(initialize2 "jdbc:postgresql://cider-ci:cider-ci@localhost:/cider-ci_v4" {})

;### Debug ####################################################################
;(debug/debug-ns *ns*)
;(logging-config/set-logger! :level :debug)
(logging-config/set-logger! :level :info)
