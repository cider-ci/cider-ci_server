; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.repository.web.project-configuration
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer [keyword str]])

  (:require
    [cider-ci.repository.project-configuration :refer [build-project-configuration]]
    [cider-ci.repository.web.shared :refer :all]

    [ring.util.response :refer [charset]]
    [clojure.data.json :as json]

    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.debug :as debug :refer [I> I>>]]
    [logbug.ring :refer [wrap-handler-with-logging]]
    [logbug.thrown :as thrown]
    )

  )

;##### project configuration ##################################################

(defn project-configuration [request]
  (-> (try
        (when-let [content (build-project-configuration
                             (-> request :params :id))]
          {:body (json/write-str content :key-fn str)
           :headers {"Content-Type" "application/json"}})
        (catch clojure.lang.ExceptionInfo e
          (case (-> e ex-data :status )
            404 {:status 404
                 :headers {"Content-Type" "application/json"}
                 :body (json/write-str
                         (merge {:title "Project Configuration Error"
                                 :description (.getMessage e)}
                                (ex-data e)))}
            422 {:status 422
                 :body (thrown/stringify e)}
            (respond-with-500 request e)))
        (catch Throwable e
          (respond-with-500 request e)))
      (charset "UTF-8")))


;#### debug ###################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns 'cider-ci.auth.http-basic)
;(debug/debug-ns *ns*)
