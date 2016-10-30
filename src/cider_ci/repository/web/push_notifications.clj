; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.repository.web.push-notifications
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer [keyword str]])

  (:require
    [cider-ci.repository.push-notifications.core :as push-notifications]
    [cider-ci.repository.state :as state]
    [cider-ci.repository.fetch-and-update.core :as fetch-and-update]

    [compojure.core :as cpj]
    [clj-time.core :as time]

    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.debug :as debug :refer [I> I>>]]
    [logbug.ring :refer [wrap-handler-with-logging]]
    [logbug.thrown :as thrown]
    ))

(defn push-notification-handler [request]
  (let [update-token (-> request :params :update_notification_token)]
    (if-let [repository (->> (:repositories (state/get-db))
                             (map #(let [[_ v] %] v))
                             (filter #(= update-token (-> % :update_notification_token str)))
                             first)]
      (do (fetch-and-update/fetch-and-update repository)
          (push-notifications/db-update-push-notification
            (:id repository)
            #(assoc % :received_at (time/now)))
          {:status 202 :body "OK"})
      {:status 404 :body "The corresponding repository was not found"})))

(defn wrap [default-handler]
  (cpj/routes
    ; TODO: deprecate this route
    (cpj/POST "/update-notification/:update_notification_token"
              _ #'push-notification-handler)
    (cpj/POST "/push-notification/:update_notification_token"
              _ #'push-notification-handler)
    (cpj/ANY "*" _ default-handler)))



;#### debug ###################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns 'cider-ci.auth.http-basic)
;(debug/debug-ns *ns*)
