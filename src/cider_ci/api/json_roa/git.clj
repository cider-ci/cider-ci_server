; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.api.json-roa.git
  (:require
    [cider-ci.api.json-roa.links :as links]
    [cider-ci.api.pagination :as pagination]
    [compojure.core :as cpj]

    [logbug.debug :as debug]
    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    ))


(defn build-commits [request response]
  (let [context (:context request)
        query-params (:query-params request)
        ids (->> response :body :commits (map :id))
        offset (pagination/compute-offset query-params)]
    {:name "Commits"
     :self-relation (links/commits context query-params)
     :collection
     (merge
       {:relations
        (->> ids
             (map-indexed (fn [i id] [(+ i 1 offset)
                                      (links/commit context id)]))
             (into {})) }
       (when (seq ids)
         (links/next-rel
           (fn [query-params]
             (links/commits-path context query-params))
           query-params)))}))


(defn build-commit [request response]
  (let [context (-> request :context)
        id (-> response :route-params :id)]
    {:name "Commit"
     :self-relation (links/commit context id)
     :relations {:jobs (links/jobs context {:tree_id (-> response :body :tree_id )})}
     }))

(defn build [request response]
  (cpj/routes
    (cpj/GET "/commits/" request (build-commits request response))
    (cpj/GET "/commits/:id" request (build-commit request response))))



;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
