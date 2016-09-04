; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.repository.commits
  (:refer-clojure :exclude [import])
  (:require
    [cider-ci.repository.git.commits :as git-commits]
    [cider-ci.repository.sql.commits :as sql.commits]

    [clojure.java.jdbc :as jdbc]

    [clojure.tools.logging :as logging]
    [logbug.debug :as debug]
    [clj-logging-config.log4j :as logging-config]
    ))

;### create / update ###########################################################

(defn insert-submodules [tx id repository-path]
  (doseq [submodule (git-commits/get-submodules id repository-path)]
    (jdbc/insert! tx :submodules
                  (assoc submodule :commit_id id))))

(defn- create [tx id repository-path]
  (let [params (git-commits/get id repository-path)
        commit (sql.commits/create! tx params)]
    (insert-submodules tx id repository-path)
    commit
    ))

;### arcs ######################################################################

(defn find-arc [ds arc]
  (first (jdbc/query ds ["SELECT * FROM commit_arcs
                         WHERE child_id = ? AND parent_id = ?",
                 (:child_id arc), (:parent_id arc)])))

(defn find-or-create-arc! [ds arc]
  (or
    (find-arc  ds arc)
    (jdbc/insert! ds :commit_arcs arc)))

(defn- create-arcs [tx id repository-path]
  (loop [to-be-imported-arcs (git-commits/arcs-to-parents id repository-path)]
    (when-let [current-arc (first to-be-imported-arcs)]
      (let [parent-id (:parent_id current-arc)
            discovered-arcs (if (sql.commits/find tx parent-id)
                              []
                              (do (create tx parent-id repository-path)
                                (git-commits/arcs-to-parents parent-id repository-path)))]
        (find-or-create-arc! tx current-arc)
        (recur (concat (rest to-be-imported-arcs) discovered-arcs))))))

;### import-recursively #######################################################

(defn import-recursively [tx id repository-path]
  (or (sql.commits/find tx id)
      (let [commit (create tx id repository-path)]
        (create-arcs tx id repository-path)
        (sql.commits/update-depths tx)
        commit)))


;#### debug ###################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)

