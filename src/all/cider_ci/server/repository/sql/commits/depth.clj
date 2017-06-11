; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.server.repository.sql.commits.depth
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer [keyword str]])

  (:require
    [cider-ci.utils.rdbms :as rdbms]
    [clojure.java.jdbc :as jdbc]
    [clojure.tools.logging :as logging]
    ))


(def ^:private update-depths-where-query
  "NOT EXISTS
  (SELECT 1
  FROM commit_arcs
  WHERE commits.id = commit_arcs.child_id)
  AND commits.depth IS NULL ")

(defn- update-root-depths [ds]
  (first (jdbc/update! ds :commits
                {:depth 0}
                [update-depths-where-query])))

(def ^:private update-next-non-root-dephts-query
  "UPDATE commits AS children
  SET depth = parents.depth + 1
  FROM commits AS parents,
  commit_arcs
  WHERE children.depth IS NULL
  AND parents.depth IS NOT NULL
  AND children.id = commit_arcs.child_id
  AND parents.id = commit_arcs.parent_id")

(defn- update-next-non-root-dephts [ds]
  (first (jdbc/execute! ds [update-next-non-root-dephts-query])))


(defn update-depths
  "Update the commits.depth columns if not null and return
  the number of updated rows."
  [ds]
  (loop [total-count (update-root-depths ds)]
    (let [round-count (update-next-non-root-dephts ds)]
      (if (< 0 round-count)
        (recur (+ total-count round-count))
        total-count))))

;(update-depths (rdbms/get-ds))

