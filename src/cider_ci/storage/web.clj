; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.storage.web
  (:require
    [cider-ci.storage.put-file :as put-file]
    [cider-ci.storage.web.public :as public]
    [cider-ci.storage.shared :refer :all]

    [cider-ci.auth.authorize :as authorize]
    [cider-ci.auth.http-basic :as http-basic]
    [cider-ci.auth.session :as session]
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
  (I> (wrap-handler-with-logging :trace)
      storage-routes
      cpj.handler/api))

(defn wrap-auth [handler]
  (I> (wrap-handler-with-logging :trace)
      handler
      (authorize/wrap-require! {:user true :service true :executor true})
      (http-basic/wrap {:user true :service true :executor true})
      session/wrap
      cookies/wrap-cookies
      cors/wrap
      (public/wrap-shortcut handler)))

(defn build-main-handler [context]
  (I> (wrap-handler-with-logging :trace)
      base-handler
      wrap-auth
      status/wrap
      (routing/wrap-prefix context)))


;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(debug/wrap-with-log-debug #'executor-may-upload-tree-attachments?)
;26e3c8953dee7a179f1b5152570f5388b75c3453(debug/wrap-with-log-debug #'put-authorized?)
;(debug/wrap-with-log-debug #'find-store)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns 'cider-ci.auth.session)
;(debug/debug-ns *ns*)
