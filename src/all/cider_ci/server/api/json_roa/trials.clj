; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.server.api.json-roa.trials
  (:require
    [cider-ci.server.api.json-roa.links :as links]
    [cider-ci.server.api.pagination :as pagination]
    [logbug.debug :as debug]
    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging])
  )


(defn build [request response]
  (let [context (:context request)
        query-params (:query-prarams request)
        task-id (-> request :params :id)
        ids (->> response :body :trials (map :id))]
    {:name "Trials"
     :self-relation (links/trials context task-id  query-params)
     :relations
     {:task (links/task context task-id)
      :retry (links/retry context task-id)
      }
     :collection
     (conj
       {:relations
        (into {}
              (map-indexed
                (fn [i id]
                  [(+ 1 i (pagination/compute-offset query-params))
                   (links/trial context id)                    ])
                ids))}
       (when (seq ids)
         (links/next-rel
           #(links/trials-path context task-id %)
           query-params)))}))



;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)

