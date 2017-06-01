; Copyright © 2013 - 2017 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.


(ns cider-ci.web
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer [keyword str]])
  (:require
    [cider-ci.api.web]
    [cider-ci.auth.authentication :as authentication]
    [cider-ci.builder.web]
    [cider-ci.client.web]
    [cider-ci.create-initial-admin.web :as create-initial-admin]
    [cider-ci.dispatcher.web]
    [cider-ci.executors]
    [cider-ci.repository.web]
    [cider-ci.server.web]
    [cider-ci.storage.web]
    [cider-ci.ui2.web]
    [cider-ci.users.web]
    [cider-ci.utils.routing :as routing]
    [cider-ci.utils.shutdown :as shutdown]

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

(def api-handler
  (cider-ci.api.web/build-main-handler "/api"))

(def builder-handler
  (cider-ci.builder.web/build-main-handler "/builder"))

(def dispatcher-handler
  (cider-ci.dispatcher.web/build-main-handler "/dispatcher"))

(def repositories-handler
  (cider-ci.repository.web/build-main-handler "/repositories"))

(def storage-handler
  (cider-ci.storage.web/build-main-handler "/storage"))

(def ui2-handler
  (cider-ci.ui2.web/build-main-handler "/ui2" ))

(def redirect-to-ui2
  (ring.util.response/redirect "/cider-ci/ui2/"))

(def server-handler
  (cider-ci.server.web/build-main-handler "/server"))


;;; routes ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def routes
  (cpj/routes
    (cpj/ANY "/api/*" [] api-handler)
    (cpj/ANY "/builder/*" [] builder-handler)
    (cpj/ANY "/dispatcher/*" [] dispatcher-handler)
    (cpj/ANY "/executors/*" [] cider-ci.executors/routes)
    (cpj/ANY "/repositories/*" [] repositories-handler)
    (cpj/ANY "/server/*" [] server-handler)
    (cpj/ANY "/storage/*" [] storage-handler)
    (cpj/ANY "/ui2/*" [] ui2-handler)
    (cpj/ANY "/users/*" [] cider-ci.users.web/routes)
    (cpj/GET "/api" [] (ring.util.response/redirect "/cider-ci/api/"))
    (cpj/GET "/storage" [] (ring.util.response/redirect "/cider-ci/storage/"))
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
      cider-ci.client.web/wrap
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
;(debug/debug-ns *ns*)
;(debug/debug-ns 'cider-ci.utils.shutdown)

