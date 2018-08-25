; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.server.dispatcher.sync.update-executor
  (:refer-clojure :exclude [update])
  (:require
    [cider-ci.server.executors-old :as executors]

    [cider-ci.utils.self :as self]
    [cider-ci.utils.http :as http]
    [cider-ci.utils.rdbms :as rdbms :refer [get-ds]]
    [cider-ci.utils.jdbc :refer [insert-or-update]]

    [clojure.data.json :as json]
    [clojure.java.jdbc :as jdbc]

    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher :refer [snatch]]
    [logbug.debug :as debug]
    [logbug.ring :refer [wrap-handler-with-logging]]
    ))


(defn get-executor [id]
  (first (jdbc/query
           (rdbms/get-ds)
           ["SELECT * from executors WHERE id = ?"
            id ])))

(defn update-traits [executor data]
  (if (= (sort (:traits executor)) (sort (:traits data)))
    executor
    (do (jdbc/update!
          (rdbms/get-ds)
          :executors
          {:traits (sort (:traits data))}
          ["id = ?" (:id executor)])
        (get-executor (:id executor)))))

(defn update-accepted-repositories [executor data]
  (if (= (sort (:accepted_repositories executor))
         (sort (:accepted_repositories data)))
    executor
    (do (jdbc/update!
          (rdbms/get-ds)
          :executors
          {:accepted_repositories (sort (:accepted_repositories data))}
          ["id = ?" (:id executor)])
        (get-executor (:id executor)))))

(defn update-max-load [executor data]
  (if (= (float (:max_load executor))
         (float (:max_load data)))
    executor
    (do (jdbc/update!
          (rdbms/get-ds)
          :executors
          {:max_load (:max_load data)}
          ["id = ?" (:id executor)])
        (get-executor (:id executor)))))

(defn update-temporary-overload-factor [executor data]
  (if-let [temporary-overload-factor (:temporary_overload_factor executor)]
    (if (= temporary-overload-factor (:temporary_overload_factor data))
      executor
      (do (jdbc/update!
            (rdbms/get-ds)
            :executors
            {:temporary_overload_factor (:temporary_overload_factor data)}
            ["id = ?" (:id executor)])
          (get-executor (:id executor))))
    executor))

(defn update-version [executor data]
  (let [executor-version (-> data :status :version)
        issue-id (clj-uuid/v5 clj-uuid/+null+ (str :version_mismatch (:id executor)))
        where-clause ["executor_id = ? AND id = ?" (:id executor) issue-id]]
    (if (= executor-version (self/version))
      (jdbc/delete! (get-ds) :executor_issues where-clause)
      (insert-or-update (get-ds)
        "executor_issues"  where-clause
        {:id issue-id
         :executor_id (:id executor)
         :title "Version Mismatch"
         :description (str "The executor `" (:name executor) "` is on version `"
                           executor-version "`, but we are on `"
                           (self/version) "`! \n"
                           "No trials will be dispatched to this executor!\n"
                           )}))
    (if (= (:version executor) executor-version)
      executor
      (do
        (jdbc/update!
          (rdbms/get-ds)
          :executors
          {:version (-> data :status :version) }
          ["id = ?" (:id executor)])
        (get-executor (:id executor))))))

(defn update-last-sync-at [executor]
  (executors/update-last-sync-at (:id executor))
  executor)

(defn update [executor data]
  (-> executor
      (update-traits data)
      (update-accepted-repositories data)
      (update-max-load data)
      (update-temporary-overload-factor data)
      (update-version data)
      update-last-sync-at))

;#### debug ###################################################################
;(debug/debug-ns *ns*)
;(debug/re-apply-last-argument #'update-when-changed)
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
