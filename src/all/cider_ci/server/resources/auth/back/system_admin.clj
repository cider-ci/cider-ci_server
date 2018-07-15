(ns cider-ci.server.resources.auth.back.system-admin
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer [keyword str presence]])
  (:require
    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug]
    [logbug.thrown :as thrown]
    ))


(defn extract-secret [request]
  (when-let [auth-header (-> request :headers :authorization)]
    (->> auth-header
         (re-find #"(?i)^secret\s+(.*)$")
         last presence)))

(defn authenticate [request]
  (if-let [auth-secret (extract-secret request)]
    (if (= auth-secret (-> request :secret-ba String.))
      (assoc request :authenticated-entity
             {:name "system-admin"
              :is_admin true
              :is_system_admin true
              :scope_read true
              :scope_write true
              :scope_admin_read true 
              :scope_admin_write true})
      (throw (ex-info "Correct secret for system-admin required!"
                      {:status 401})))
    request))

(defn wrap [handler]
  (fn [request]
    (handler (authenticate request))))

;#### debug ###################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns 'cider-ci.utils.shutdown)
;(debug/debug-ns 'cider-ci.open-session.encryptor)
;(debug/debug-ns *ns*)
