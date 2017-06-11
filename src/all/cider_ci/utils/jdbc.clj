(ns cider-ci.utils.jdbc
  (:require
    [clojure.java.jdbc :as jdbc]

    [logbug.catcher :as catcher]
    [clojure.tools.logging :as logging]
    ))

(defn insert-or-update [tx table where-clause values]
  (let [[clause & params] where-clause]
    (if (first (jdbc/query
                 tx
                 (concat [(str "SELECT 1 FROM " table " WHERE " clause)]
                         params)))
      (jdbc/update! tx table values where-clause)
      (jdbc/insert! tx table values))))

