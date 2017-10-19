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
    [cider-ci.server.trees.attachments.web]
    [cider-ci.server.builder.web]
    [cider-ci.server.client.web]
    [cider-ci.server.client.web]
    [cider-ci.server.commits.web]
    [cider-ci.server.create-initial-admin.web :as create-initial-admin]
    [cider-ci.server.dispatcher.web]
    [cider-ci.server.executors]
    [cider-ci.server.jobs.web]
    [cider-ci.server.repository.web]
    [cider-ci.server.session.web]
    [cider-ci.server.socket]
    [cider-ci.server.storage.web]
    [cider-ci.server.trees]
    [cider-ci.server.users.web]
    [cider-ci.server.web]

    [cider-ci.utils.http-resources-cache-buster :refer [wrap-resource]]
    [cider-ci.utils.routing :as routing]
    [cider-ci.utils.shutdown :as shutdown]
    [cider-ci.utils.status :as status]

    [compojure.core :as cpj]
    [ring.middleware.content-type :refer [wrap-content-type]]
    [ring.middleware.default-charset :refer [wrap-default-charset]]
    [ring.middleware.defaults :refer [site-defaults wrap-defaults]]
    [ring.middleware.not-modified :refer [wrap-not-modified]]
    [ring.util.response]

    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.debug :as debug :refer [I> I>> identity-with-logging]]
    [logbug.ring :refer [wrap-handler-with-logging]]
    [logbug.thrown :as thrown]
    ;[ring.mock.request :as ring-mock]
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

(def client-handler
  (cider-ci.server.client.web/build-main-handler "/client" ))

(def redirect-to-client
  (ring.util.response/redirect "/cider-ci/"))

(def push-handler
  (I> wrap-handler-with-logging
      cider-ci.server.socket/routes
            (routing/wrap-prefix "/cider-ci/server")))

;;; routes ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def routes
  (cpj/routes
    (cpj/ANY "/api/*" [] api-handler)
    (cpj/ANY "/builder/*" [] builder-handler)
    (cpj/ANY "/commits/*" [] cider-ci.server.commits.web/routes)
    (cpj/ANY "/dispatcher/*" [] dispatcher-handler)
    (cpj/ANY "/executors/*" [] cider-ci.server.executors/routes)
    (cpj/ANY "/jobs/*" [] cider-ci.server.jobs.web/routes)
    (cpj/ANY "/repositories/*" [] repositories-handler)
    (cpj/ANY "/storage/*" [] storage-handler)
    (cpj/ANY "/client/*" [] client-handler)
    (cpj/ANY "/users/*" [] cider-ci.server.users.web/routes)
    (cpj/ANY "/api" [] (ring.util.response/redirect "/cider-ci/api/"))
    (cpj/ANY "/storage" [] (ring.util.response/redirect "/cider-ci/storage/"))
    (cpj/ANY "/tree-attachments/*" [] cider-ci.server.trees.attachments.web/routes)
    ;(cpj/ANY "/trial-attachments/*" [] cider-ci.server.attachments.web/routes)
    (cpj/ANY "/session*" [] #'cider-ci.server.session.web/routes)
    (cpj/ANY "/server/ws*" [] cider-ci.server.socket/routes)
    (cpj/ANY "/trees/*" []  cider-ci.server.trees/routes)
    (cpj/GET "/" [] redirect-to-client)
    (cpj/ANY "*" [] dead-end-handler)))


;;; http-static files caching ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

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
      ring.middleware.json/wrap-json-response
      wrap-accept
      (ring.middleware.defaults/wrap-defaults {:proxy true})
      (wrap-resource
        "public" {:allow-symlinks? true
                  :cache-bust-paths ["/css/site.css"
                                     "/css/site.min.css"
                                     "/js/app.js"]
                  :never-expire-paths [#".*font-awesome-\d\.\d\.\d\/.*"]})
      wrap-content-type
      (wrap-default-charset "UTF-8")
      wrap-not-modified
      status/wrap
      (routing/wrap-prefix "/cider-ci")
      (ring.middleware.json/wrap-json-body {:keywords? true})
      routing/wrap-exception))


;#### debug ###################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns 'cider-ci.utils.shutdown)
;(debug/debug-ns *ns*)
