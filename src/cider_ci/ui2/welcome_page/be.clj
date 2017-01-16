(ns cider-ci.ui2.welcome-page.be
  (:refer-clojure :exclude [str keyword])
  (:require
    [cider-ci.ui2.constants :refer [CONTEXT]]
    [cider-ci.ui2.web.shared :as shared]

    [cider-ci.auth.authorize :as authorize]

    [cider-ci.utils.rdbms :as rdbms]

    [clojure.java.jdbc :as jdbc]
    [compojure.core :as cpj]

    [logbug.debug :as debug]
    ))

(defn get-welcome-page-settings [req]
  (Thread/sleep 1000)
  {:body (or (->> ["SELECT * FROM welcome_page_settings WHERE id = 0"]
                  (jdbc/query (rdbms/get-ds) )
                  first)
             {})})

(defn patch-welcome-page-settings [req]
  (Thread/sleep 1000)
  (if (->> ["SELECT true AS exists FROM welcome_page_settings"]
           (jdbc/query (rdbms/get-ds))
           first :exists)
    (jdbc/update! (rdbms/get-ds)
                  :welcome_page_settings (:body req) [])
    (jdbc/insert! (rdbms/get-ds)
                  :welcome_page_settings (merge {:id 0} (:body req))))
  {:status 200})

(defn wrap [handler]
  (cpj/routes
    (cpj/GET "/welcome-page-settings" [] #'get-welcome-page-settings)
    (cpj/PATCH "/welcome-page-settings" [] (authorize/wrap-require!
                                             #'patch-welcome-page-settings
                                             {:admin true}))
    (cpj/ANY "*" [] handler)))

;(debug/debug-ns 'cider-ci.auth.authorize)
;(debug/debug-ns *ns*)
