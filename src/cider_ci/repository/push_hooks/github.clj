; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.repository.push-hooks.github
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer [keyword str]])
  (:require
    [cider-ci.repository.constants :refer [CONTEXT]]
    [cider-ci.repository.state :as state]
    [cider-ci.utils.config :refer [get-config]]
    [cider-ci.utils.rdbms :as rdbms]
    [cider-ci.utils.url :as url]
    [clojure.data.json :as json]
    [clj-http.client :as http-client]
    [clojure.java.jdbc :as jdbc])
  (:require
    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher :refer [snatch]]
    [logbug.debug :as debug :refer [I> I>> identity-with-logging]]
    [logbug.thrown :as thrown]
    ))

;### helpers ##################################################################

(defn remote_api_endpoint! [repository]
  (let [endpoint (or (:remote_api_endpoint repository)
                     (when (= "github.com"
                              (-> repository :git_url url/dissect :host))
                       "https://api.github.com"))]
    (assert (not (clojure.string/blank? endpoint)))
    endpoint))

(defn remote_api_namespace! [repository]
  (let [project-ns (or (:remote_api_namespace repository)
                       (-> repository :git_url url/dissect :project_namespace))]
    (assert (not (clojure.string/blank? project-ns)))
    project-ns))

(defn remote_api_name! [repository]
  (let [project-name (or (:remote_api_name repository)
                         (-> repository :git_url url/dissect :project_name))]
    (assert (not (clojure.string/blank? project-name)))
    project-name))

;(-> "https://github.com/cider-ci/cider-ci_demo-project-bash.git"  url/dissect )

;##############################################################################
(defn hooks-url [repository]
  (str (remote_api_endpoint! repository)
       "/repos/" (remote_api_namespace! repository)
       "/" (remote_api_name! repository) "/hooks"))

;(hooks-url {:git_url "https://github.com/cider-ci/cider-ci_demo-project-bash.git"})

(defn get-hooks [repository]
  (Thread/sleep 1000)
  (let [url (hooks-url repository)
        resp (http-client/get
               url {:oauth-token (:remote_api_token repository)
                    :as :json
                    :accept :json})]
    (assert (= (:status resp) 200))
    (-> resp :body)))

(defn notification-url [repository]
  (str (-> (get-config) :server_base_url)
       CONTEXT "/update-notification/"
       (:update_notification_token repository)))

(defn push-hook [repository]
  (let [notification-url (notification-url repository)
        hooks (get-hooks repository)]
    (some (fn [hook]
            (and (= notification-url
                    (-> hook :config :url))
                 hook))
          hooks)))

;##############################################################################

(defn create-push-hook [repository]
  (Thread/sleep 1000)
  (let [notification-url (notification-url repository)
        post-url (hooks-url repository)
        payload {:name "web"
                 :config {:content_type "json"
                          :url notification-url }
                 :active true}
        resp (http-client/post
               post-url {:oauth-token (:remote_api_token repository)
                         :body (json/write-str payload)
                         :as :json
                         :accept :json})]
    (assert (= (:status resp) 201))
    (:body resp)))

;##############################################################################

(defn test-push-hook [hook repository]
  (Thread/sleep 1000)
  (let [url (-> hook :test_url)
        resp (http-client/post
               url {:oauth-token (:remote_api_token repository)})]
    (and (= (:status resp) 204) hook)))


;##############################################################################

(defn setup-and-check-pushhook [repository]
  (let [hook (or (push-hook repository)
                 (create-push-hook repository))]
    (assert hook)
    (if-let [hook (test-push-hook hook repository)]
      {:hook hook
       :state "ok"}
      {:state "error"})))


;(setup-and-check-pushhook (first (map second (:repositories (state/get-db)))))

;#### debug ###################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
