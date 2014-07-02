(ns cider-ci.sql.core
  (:require 
    [clojure.java.jdbc :as jdbc]
    [clojure.tools.logging :as logging]
    [cider-ci.utils.with :as with]
    )
  (:import 
    [com.mchange.v2.c3p0 ComboPooledDataSource]
    ))

(declare 
  get-ds
  ds
  set-ds
  )


(defonce conf (atom {}))

(defn initialize [new-conf]
  (logging/info [initialize new-conf])
  (reset! conf new-conf)
  (when @ds
    (.reset @ds)
    (reset! ds nil))
  (set-ds new-conf)
  (logging/info "initialized"))

(def ^:private ds (atom  nil))

(defn get-ds [] 
  (or @ds
      (throw (IllegalStateException. "Datasource is not initialized."))))

(defn ^:private set-ds [db-conf]
  (logging/info "initializing ds with config: " db-conf)
  (with/logging
    (reset! 
      ds 
      (let [datasource (ComboPooledDataSource.)
            db (:database db-conf)]
        ;(.setDriverClass datasource (:classname db))
        (.setJdbcUrl datasource (str "jdbc:" (:subprotocol db) "://" (:subname db) "/"  (:database db)))
        (when-let [username (:username db)]
          (.setUser datasource username))
        (when-let [password (:password db)]
          (.setPassword datasource password))
        ;(.setMaxIdleTimeExcessConnections datasource (* 30 60))
        ;(.setMaxIdleTime datasource (* 3 60 60))
        (.setMaxPoolSize datasource (:pool db))
        {:datasource datasource}))))



;### utils ####################################################################

(defn placeholders [col] 
  (->> col 
    (map (fn [_] "?"))
    (clojure.string/join  ", ")))
  ;(placeholders (range 1 5))


