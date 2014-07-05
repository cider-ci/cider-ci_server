(ns cider-ci.utils.rdbms
  (:require 
    [clojure.java.jdbc :as jdbc]
    [clojure.tools.logging :as logging]
    )
  (:import 
    [com.mchange.v2.c3p0 ComboPooledDataSource]
    ))


(defn get-columns-metadata [table-name db-conf]
  (jdbc/with-db-metadata [md db-conf]
    (into {} (sort 
               (map (fn [column-data]
                      [(keyword (:column_name column-data))
                       column-data])
                    (jdbc/metadata-result 
                      (.getColumns md nil nil table-name "")))))))

(defn get-table-metadata [db-conf]
  (into {} (sort 
             (jdbc/with-db-metadata [md db-conf]
               (map
                 (fn [table-data]
                   [(keyword (:table_name table-data))
                    (conj table-data
                          {:columns (get-columns-metadata 
                                      (:table_name table-data) db-conf)})])
                 (jdbc/metadata-result 
                   (.getTables md nil nil nil (into-array ["TABLE" "VIEW"])))
                 )))))

(defn create-c3p0-pooled-datasource [db-conf]
  (logging/debug create-c3p0-pooled-datasource [db-conf])
  (doto (ComboPooledDataSource.)
    (.setJdbcUrl (str "jdbc:" (:subprotocol db-conf) ":" (:subname db-conf)))
    (#(when-let [user (:user db-conf)] (.setUser % user)))
    (#(when-let [password (:password db-conf)] (.setPassword % password)))
    (#(when-let [max-pool-size (:max_pool_size db-conf)](.setMaxPoolSize % max-pool-size)))))


(defn create-ds [db-conf]
  (logging/info create-ds [db-conf])
  {:datasource (create-c3p0-pooled-datasource db-conf)
   :table-metadata (get-table-metadata db-conf)
   })


