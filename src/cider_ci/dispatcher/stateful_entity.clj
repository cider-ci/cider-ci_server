; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.dispatcher.stateful-entity
  (:require
    [logbug.debug :as debug]
    [cider-ci.utils.rdbms :as rdbms]
    [logbug.catcher :as catcher]
    [clj-logging-config.log4j :as logging-config]
    [clojure.java.jdbc :as jdbc]
    [clojure.tools.logging :as logging]
    ))



(defn- assert-existence [tablename id]
  (when-not (first (jdbc/query (rdbms/get-ds)
                               [(str "SELECT 1 FROM " (name tablename)
                                     " WHERE id = ?" ) id]))
    (throw (IllegalArgumentException.
             (str "The entity does not exist" {:tablename tablename :id id})))))

(defn update-state
  "Updates the state. Returns true if state has changed and false otherwise.
  Asserts existence of the target row if and only if option is given and
  (:assert-existence options) evaluates to true."

  ([tablename id state]
   (update-state tablename id state {}))

  ([tablename id state options]
   (when (:assert-existence options) (assert-existence tablename id))
   (->> (jdbc/update!
          (rdbms/get-ds)
          tablename
          {:state state}
          [(str "id = ? AND state != '" state "'") id])
        first (not= 0))))


(defn evaluate-and-update [tablename id states]
  (catcher/with-logging {}
    (let [update-to #(update-state tablename id % {:assert-existence true})]
      (cond
        (some #{"passed"} states) (update-to "passed")
        (every? #{"aborted"} states) (update-to "aborted")
        (every? #{"failed" "aborted"} states) (update-to "failed")
        (some #{"executing"} states) (update-to "executing")
        (some #{"pending" "dispatching"} states) (update-to "pending")
        (empty? states) false
        :else (throw (IllegalStateException. "NOOOO"))
        ))))

;#### debug ###################################################################
;(debug/debug-ns *ns*)
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
