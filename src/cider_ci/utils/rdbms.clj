(ns cider-ci.utils.rdbms
  (:require
    [drtom.logbug.catcher :as catcher]
    [clojure.java.jdbc :as jdbc]
    [clojure.tools.logging :as logging]
    [ring.util.codec]
    [pg-types.all]
    )
  (:import
    [java.net.URI]
    [com.mchange.v2.c3p0 ComboPooledDataSource DataSources]
    ))


(defonce ^:private ds (atom nil))
(defn get-ds [] @ds)

(defn check-connection
  "Performs a simple query an returns boolean true on success and
  false otherwise."
  []
  (try
    (catcher/wrap-with-log-error
      (->> (jdbc/query (get-ds) ["SELECT true AS state"])
           first :state))
    (catch Exception _
      false)))



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
             (#(when-let [user (get-user db-conf)] (.setUser % user)))
             (#(when-let [password (get-password db-conf)] (.setPassword % password)))
             (#(when-let [max-pool-size (get-max-pool-size db-conf)](.setMaxPoolSize % max-pool-size))))}))

(defn initialize [db-conf]
  (logging/info initialize [db-conf])
  (create-c3p0-datasources db-conf)
  (.addShutdownHook (Runtime/getRuntime)
                    (Thread. (fn []
                               (reset)))))

;(initialize {:subprotocol "sqlite" :subname ":memory:"})
