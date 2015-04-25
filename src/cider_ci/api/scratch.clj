; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.api.scratch
  (require [honeysql.core :as sql]
           [honeysql.helpers :refer :all]
           [clj-logging-config.log4j :as logging-config]
           [clojure.tools.logging :as logging]
           [drtom.logbug.debug :as debug]
           )) 

;(-> (select ":%lower(branches.name))" (from :foo) sql/format))



(def sqlmap {:select [:a :b :c]
             :from [:foo]
             :where [:= :%lower.branch.name "?name"]})

(def m2 (-> 
          (select :jobs.id :jobs.created_at)
          (modifiers :distinct)
          (sql/format)
          ))

(def base-query 
  (->
    (select :jobs.id :jobs.created_at)
    (modifiers :distinct)
    (join :commits [:= :commits.tree_id :jobs_tree_id])
    (merge-join :commits [:= :commits.tree_id :jobs_tree_id])
    ;(merge-join :branches_commits [:= :branches_commits.commit_id :commits.id])
    ;(sql/format)
    ))


(def double-join
  (-> 
    (join :t1 [:= :t0.x :t1.x])
    (merge-join :t1 [:= :t0.x :t1.x])
    (merge-join :t3 [:= :t3.x :t1.x])
    ))



(defn dedup-join [honeymap]
  (assoc-in honeymap [:join]
            (reduce #(let [[k v] %2] (conj %1 k v)) []
                    (distinct (partition 2 (:join honeymap))))))


(dedup-join double-join)

(reduce #(let [[k v] %2] (conj %1 k v)) []
  (distinct (partition 2 (:join double-join))))

  
(conj [] :x)
        

(into {} [[:a 1]])





(sql/format sqlmap)

;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)

