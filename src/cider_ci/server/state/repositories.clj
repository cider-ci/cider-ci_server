(ns cider-ci.server.state.repositories
  (:require
    [cider-ci.repository.state.db]
    [cider-ci.server.state.db]

    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher :refer [snatch]]
    [logbug.debug :as debug]
    [logbug.thrown :as thrown]
    ))

(defn update-repos-in-server-db []
  (swap! cider-ci.server.state.db/db
         assoc-in [:repositories]
         (get @cider-ci.repository.state.db/db :repositories {})))

(defn watch []
  (add-watch
    cider-ci.repository.state.db/db
    :update-repositories-in-server-db
    (fn [_key repo-db-ref _old _new]
      (if (not= (-> @cider-ci.repository.state.db/db :repositories)
                (-> @cider-ci.server.state.db/db :repositories))
        (#'update-repos-in-server-db)))))

(defn initialize []
  (watch)
  (update-repos-in-server-db))

;(debug/debug-ns *ns*)
