; Copyright © 2013 - 2017 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.auth.authentication.guest
  (:require
    [cider-ci.utils.rdbms :as rdbms]

    [clojure.java.jdbc :as jdbc]

    [clojure.tools.logging :as logging]
    [clj-logging-config.log4j :as logging-config]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug]
    ))

(defn admin-party? []
  (->> [(str "SELECT true admin_party WHERE NOT EXISTS "
             "(SELECT * FROM users "
             " WHERE is_admin = true)")]
       (jdbc/query (rdbms/get-ds))
       first boolean))

(defn add-guest-if-authenticated-entity-is-missing [request handler]
  (handler
    (if-not (-> request :authenticated-entity empty?)
      request
      (let [admin-party? (admin-party?)]
        (assoc request
               :authenticated-entity
               {:type :guest
                :authentication-method :session
                :scope_read true
                :scope_write admin-party?
                :scope_admin_read admin-party?
                :scope_admin_write admin-party?})))))


(defn wrap [handler]
  (fn [request]
    (add-guest-if-authenticated-entity-is-missing request handler)))

;### Debug ####################################################################
;(debug/debug-ns *ns*)
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)


