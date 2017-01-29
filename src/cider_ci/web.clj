; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.web
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer [keyword str]])
  (:require
    [cider-ci.server.web]
    [cider-ci.repository.web]
    [cider-ci.ui2.web]

    [compojure.core :as cpj]

    [ring.util.response]

    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.debug :as debug :refer [I> I>> identity-with-logging]]
    [logbug.ring :refer [wrap-handler-with-logging]]
    [logbug.thrown :as thrown]
    ))

(defn dead-end-handler [req]
  {:status 404
   :body "Not found!"})

(def repositories-handler
  (cider-ci.repository.web/build-main-handler "/cider-ci/repositories"))

(def ui2-handler
  (cider-ci.ui2.web/build-main-handler "/cider-ci/ui2" ))

(def redirect-to-ui2
  (ring.util.response/redirect "/cider-ci/ui2/"))

(def server-handler
  (cider-ci.server.web/build-main-handler "/cider-ci/server"))

(def routes
  (cpj/routes
    (cpj/ANY "/cider-ci/repositories/*" [] repositories-handler)
    (cpj/ANY "/cider-ci/ui2/*" [] ui2-handler)
    (cpj/ANY "/cider-ci/server/*" [] server-handler)
    (cpj/GET "/" [] redirect-to-ui2)
    (cpj/ANY "*" [] dead-end-handler)))

(defn build-main-handler [_]
  (I> wrap-handler-with-logging
      routes))

;#### debug ###################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns 'cider-ci.auth.http-basic)
(debug/debug-ns *ns*)
