; Copyright (C) 2013, 2014 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.storage.web.public
  (:require 
    [cider-ci.utils.debug :as debug]
    [cider-ci.utils.rdbms :as rdbms]
    [cider-ci.utils.rdbms.conversion :as rdbms.conversion]
    [clj-logging-config.log4j :as logging-config]
    [clojure.java.jdbc :as jdbc]
    [clojure.tools.logging :as logging]
    [compojure.core :as cpj]
    [compojure.handler :as cpj.handler]
    [honeysql.core :as hc]
    [honeysql.helpers :as hh]
    ))


(defn- build-basequery []
  (-> (hh/select :true)
      (hh/from :executions)
      (hh/merge-join :commits [:= :commits.tree_id :executions.tree_id])
      (hh/merge-join :branches_commits [:= :branches_commits.commit_id :commits.id])
      (hh/merge-join :branches [:= :branches.id :branches_commits.branch_id])
      (hh/merge-join :repositories [:= :repositories.id :branches.repository_id])
      (hh/merge-where [:= :repositories.public_view_permission true])
      (hh/limit 1)))

(defn- tree-attachment-public-viewable? [request]
  (let [tree-id (-> request :route-params :tree_id)
        query (-> (build-basequery) 
                  (hh/merge-where [:= :executions.tree_id tree-id])
                  hc/format)
        qres (first (jdbc/query (rdbms/get-ds) query)) ]
    ; this is not used as a http response
    ; we can't just return true/false because a ring 'response' is expected 
    (if qres
      {:public_viewable true, :status 200}
      {:public_viewable false, :status 401})))

(defn- trial-attachment-public-viewable? [request]
  (let [trial-id (-> request :route-params :trial_id 
                     rdbms.conversion/convert-to-uuid)
        query (-> (build-basequery) 
                  (hh/merge-join :tasks [:= :tasks.execution_id :executions.id])
                  (hh/merge-join :trials [:= :trials.task_id :tasks.id])
                  (hh/merge-where [:= :trials.id trial-id])
                  hc/format)
        qres (first (jdbc/query (rdbms/get-ds) query)) ]
    ; this is not used as a http response
    ; we can't just return true/false because a ring 'response' is expected 
    (if qres
      {:public_viewable true, :status 200}
      {:public_viewable false, :status 401})))


(def ^:private public-viewable?
  (cpj/routes
    (cpj/GET "/tree-attachments/:tree_id/*" _ tree-attachment-public-viewable?)
    (cpj/GET "/trial-attachments/:trial_id/*" _ trial-attachment-public-viewable?)))

(defn- resource-is-public-viewable [request]
  (or (:public_viewable (public-viewable? request))
      false))

(defn wrap-shortcut 
  "Short-cuts to the `bare-handler` if the given tree-id/trial-id
  is related to a repository that has :public_view_permission. Uses
  authenticated-handler otherwise"
  [authenticated-handler bare-handler]
  (fn [request]
    (if (resource-is-public-viewable request)
      (bare-handler request)
      (authenticated-handler request))))



;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns 'cider-ci.auth.session)
;(debug/debug-ns *ns*)
