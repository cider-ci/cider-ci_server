; Copyright © 2013 - 2017 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.


(ns cider-ci.server.web
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer [keyword str]])
  (:require
    [cider-ci.auth.authentication :as authentication]
    [cider-ci.auth.authorize :as authorize]
    [cider-ci.server.api.web]
    [cider-ci.server.builder.web]
    [cider-ci.server.client.web]
    [cider-ci.server.create-initial-admin.web :as create-initial-admin]
    [cider-ci.server.dispatcher.web]
    [cider-ci.server.executors]
    [cider-ci.server.push]
    [cider-ci.server.repository.web]
    [cider-ci.server.storage.web]
    [cider-ci.server.ui2.web]
    [cider-ci.server.users.web]
    [cider-ci.server.web]
    [cider-ci.utils.routing :as routing]
    [cider-ci.utils.shutdown :as shutdown]

    [compojure.core :as cpj]
    [ring.middleware.defaults :refer [site-defaults wrap-defaults]]
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

(def api-handler
  (cider-ci.server.api.web/build-main-handler "/api"))

(def builder-handler
  (cider-ci.server.builder.web/build-main-handler "/builder"))

(def dispatcher-handler
  (cider-ci.server.dispatcher.web/build-main-handler "/dispatcher"))

(def repositories-handler
  (cider-ci.server.repository.web/build-main-handler "/repositories"))

(def storage-handler
  (cider-ci.server.storage.web/build-main-handler "/storage"))

(def ui2-handler
  (cider-ci.server.ui2.web/build-main-handler "/ui2" ))

(def redirect-to-ui2
  (ring.util.response/redirect "/cider-ci/ui2/"))

(def push-handler
  (I> wrap-handler-with-logging
      cider-ci.server.push/routes
            (routing/wrap-prefix "/cider-ci/server")))

;;; routes ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def routes
  (cpj/routes
    (cpj/ANY "/api/*" [] api-handler)
    (cpj/ANY "/builder/*" [] builder-handler)
    (cpj/ANY "/dispatcher/*" [] dispatcher-handler)
    (cpj/ANY "/executors/*" [] cider-ci.server.executors/routes)
    (cpj/ANY "/repositories/*" [] repositories-handler)
    (cpj/ANY "/storage/*" [] storage-handler)
    (cpj/ANY "/ui2/*" [] ui2-handler)
    (cpj/ANY "/users/*" [] cider-ci.server.users.web/routes)
    (cpj/ANY "/api" [] (ring.util.response/redirect "/cider-ci/api/"))
    (cpj/ANY "/storage" [] (ring.util.response/redirect "/cider-ci/storage/"))
    (cpj/ANY "/server/ws*" [] cider-ci.server.push/routes)
    (cpj/GET "/" [] redirect-to-ui2)
    (cpj/ANY "*" [] dead-end-handler)))

;;; default wrappers ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn wrap-accept [handler]
  (ring.middleware.accept/wrap-accept
    handler
    {:mime
     ["application/json-roa+json" :qs 1 :as :json-roa
      "application/json" :qs 1 :as :json
      "text/html" :qs 1 :as :html
      ]}))

(defn build-main-handler [_]
  (I> wrap-handler-with-logging
      routes
      cider-ci.server.client.web/wrap
      shutdown/wrap
      create-initial-admin/wrap
      authentication/wrap
      ring.middleware.params/wrap-params
      (ring.middleware.json/wrap-json-body {:keywords? true})
      ring.middleware.json/wrap-json-response
      wrap-accept
      (ring.middleware.defaults/wrap-defaults {:static {:resources "public"}})
      (routing/wrap-prefix "/cider-ci")
      routing/wrap-exception))

;#### debug ###################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns 'cider-ci.utils.shutdown)
;(debug/debug-ns *ns*)

