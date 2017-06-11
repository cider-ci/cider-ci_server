; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.server.storage.web
  (:require
    [cider-ci.server.storage.put-file :as put-file]
    [cider-ci.server.storage.web.public :as public]
    [cider-ci.server.storage.shared :refer :all]

    [cider-ci.auth.authorize :as authorize]
    [cider-ci.open-session.cors :as cors]
    [cider-ci.utils.config :as config :refer [get-config]]
    [cider-ci.utils.http :as http]
    [cider-ci.utils.http-server :as http-server]
    [cider-ci.utils.rdbms :as rdbms :refer [get-ds]]
    [cider-ci.utils.routing :as routing]
    [cider-ci.utils.status :as status]

    [clojure.data.json :as json]
    [clojure.java.io :as io]
    [clojure.java.jdbc :as jdbc]
    [compojure.core :as cpj]
    [compojure.handler :as cpj.handler]
    [honeysql.sql :refer :all]
    [me.raynes.fs :as clj-fs]
    [ring.middleware.cookies :as cookies]
    [ring.util.response]

    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug :refer [I> I>> identity-with-logging]]
    [logbug.ring :refer [wrap-handler-with-logging]]
    ))

;### actions ##################################################################


(defn get-file [request]
  (catcher/snatch {}
    (when-let [store (find-store request)]
      (when-let [row (get-row request)]
        (let [file-path (-> (str (:file_path store) "/" (:id row)) clj-fs/file
                            clj-fs/absolute clj-fs/normalized .getAbsolutePath)]
          (-> (ring.util.response/file-response file-path)
              (ring.util.response/header "X-Sendfile" file-path)
              (ring.util.response/header "content-type" (:content_type row))))))))

(def storage-routes
  (-> (cpj/routes
        (cpj/GET "/:prefix/:id/*" _ get-file)
        (cpj/PUT "/:prefix/:id/*" _ put-file/put-file))))


;#### routing #################################################################

(def base-handler
  (I> wrap-handler-with-logging
      storage-routes
      cpj.handler/api))

(defn build-main-handler [context]
  (I> wrap-handler-with-logging
      base-handler
      (authorize/wrap-require! {:user true :service true :executor true})
      (public/wrap-shortcut base-handler)
      cors/wrap
      (routing/wrap-prefix context)))


;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(debug/wrap-with-log-debug #'executor-may-upload-tree-attachments?)
;(debug/wrap-with-log-debug #'put-authorized?)
;(debug/wrap-with-log-debug #'find-store)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
