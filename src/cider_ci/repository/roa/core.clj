(ns cider-ci.repository.roa.core
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer [keyword str]])

  (:require
    [cider-ci.utils.core :refer [deep-merge]]
    [cider-ci.repository.constants :refer :all]

    [compojure.core :as cpj]
    [ring.util.response :as ring.response]

    [logbug.debug :as debug]
    [logbug.catcher :as catcher]
    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]))


(def projects-path (str CONTEXT "/projects/"))

(def root-relation {:root {:href "/cider-ci/api/"
                           :name "API Root"}})

(def projects-specs-relation
  {:specs
   {:name "Specs"
    :href "https://github.com/cider-ci/cider-ci_integration-tests/blob/master/spec/features/projects_management-via-api_spec.rb"}})

(def projects-relation
  {:name "Projects"
   :href projects-path
   :relations projects-specs-relation})

(defn get-projects [request-reponse]
  (let [response (:response request-reponse)]
    (-> response
        (deep-merge {:body
                     {:_json-roa
                      {:json-roa_version "1.0.0"
                       :name "Projects"
                       :self-relation projects-relation
                       :collection {:relations
                                    (->> response :body :projects
                                         (map-indexed
                                           (fn [idx p]
                                             [idx {:name "Project"
                                                   :href (str projects-path (:id p))
                                                   :methods {:get {} :patch {} :delete {}}
                                                   :relations projects-specs-relation}]))
                                         (into {}))}
                       :relations (deep-merge {}
                                   root-relation
                                   {:create_project
                                    {:name "Create Project"
                                     :href projects-path
                                     :methods {:post {}}
                                     :relations projects-specs-relation}})}}})
        (ring.response/header "Content-Type" "application/json-roa+json"))))

(defn get-project [request-reponse]
  (let [response (:response request-reponse)]
    (-> response
        (deep-merge {:body
                     {:_json-roa
                      {:json-roa_version "1.0.0"
                       :name "Project"
                       :self-relation
                       {:name "Project"
                        :href (str projects-path (-> request-reponse :route-params :id))
                        :relations projects-specs-relation}
                       :relations
                       {:projects projects-relation}}}})
        (ring.response/header "Content-Type" "application/json-roa+json"))))

(defn default [request-reponse]
  (let [response (:response request-reponse)]
    (cond
      (map? (:body response)) (-> response
                                  (deep-merge {:body
                                               {:_json-roa
                                                {:json-roa_version "1.0.0"
                                                 :relations root-relation}}}))
      :else response)))

(def routes
  (cpj/routes
    (cpj/GET "/projects/" [] #'get-projects)
    (cpj/GET "/projects/:id" [] #'get-project)
    (cpj/ANY "*" [] #'default)
    ))

(defn- _wrap [handler request]
  (let [response (handler request)]
    (def ^:dynamic *request* request)
    (def ^:dynamic *response* response)
    (if (= (-> request :accept :mime ) :json-roa)
      (routes (assoc request :response response))
      response)))

(defn wrap [handler]
  (fn [request]
    (_wrap handler request)))

;#### debug ###################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
