; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.auth.authorize
  (:require
    [clojure.tools.logging :as logging]
    [clj-logging-config.log4j :as logging-config]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug]
    ))

(defn system-admin? [request]
  (= (-> request :authenticated-entity :type) :system-admin))

(defn guest? [request]
  (= (-> request :authenticated-entity :type) :guest))

(defn user? [request]
  (= (-> request :authenticated-entity :type) :user))

(defn forbidden [options-fn]
  {:status 403
   :body (str "The authorization requirements are not satisfied: " options-fn)})

(defn eval-fn [request fun] (fun request))

(defn eval-options [request options]
  (or (and (:guest options) (guest? request))
      (and (:user options) (user? request))
      (and (:admin options)
           (-> request :authenticated-entity :scope_admin_read)
           (-> request :authenticated-entity :scope_admin_write))
      (and (:service options)
           (= (-> request :authenticated-entity :type) :service))
      (and (:executor options)
           (= (-> request :authenticated-entity :type) :executor))))


(defn- dispatch-optoins-fn [request options-fn]
  (if (fn? options-fn)
    (eval-fn request options-fn)
    (eval-options request options-fn)))

(defn- require! [handler request options-fn]
  (logging/debug 'wrap-require!
                 {:handler handler :options-fn options-fn :request request})
  (if (or (system-admin? request)
          (dispatch-optoins-fn request options-fn))
    (handler request)
    (if (guest? request)
      {:status 401}
      {:status 403
       :body (str "The authorization requirements are not satisfied: " options-fn)})))

(defn wrap-require! [handler options-fn]
  (fn [request]
    (require! handler request options-fn)))


;### Debug ####################################################################
;(debug/debug-ns *ns*)
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)


