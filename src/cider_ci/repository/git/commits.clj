; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.


(ns cider-ci.repository.git.commits
  (:refer-clojure :exclude [str keyword get])
  (:require [cider-ci.utils.core :refer [keyword str]])
  (:require
    [cider-ci.utils.system :as system]

    [clj-commons-exec :as commons-exec]
    [clj-time.coerce :as time-coerce]
    [clj-time.core :as time-core]
    [clj-time.format :as time-format]
    [clojure.string :as string :refer [split]]

    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug]
    [logbug.thrown :as thrown]
    ))

;##############################################################################

(defn- trim-line [lines n]
  (clojure.string/trim (nth lines n)))

(defn- parse-time [ts]
  (time-coerce/to-sql-time (time-format/parse (time-format/formatters :rfc822) ts)))

(defn get [id repository-path]
  (logging/debug "get-info " id repository-path)
  (let [res (system/exec!
              ["git" "log" "-n" "1" "--pretty=%T %n%an %n%ae %n%aD %n%cn %n%ce %n%cD %n%s %n%b" id]
              {:dir repository-path})
        out (:out res)
        lines (clojure.string/split out #"\n")]
    (logging/debug (count lines) " lines: " lines)
    {:id id
     :tree_id (clojure.string/trim (nth lines 0))
     :author_name (clojure.string/trim (nth lines 1))
     :author_email (clojure.string/trim (nth lines 2))
     :author_date (parse-time (trim-line lines 3))
     :committer_name (clojure.string/trim (nth lines 4))
     :committer_email (clojure.string/trim (nth lines 5))
     :committer_date (parse-time (trim-line lines 6))
     :subject (clojure.string/trim (nth lines 7))
     :body (clojure.string/join "\n" (drop 8 lines))
     }))

;##############################################################################

(defn get-git-parent-ids [id repository-path]
  (let [res (system/exec!
              ["git" "rev-list" "-n" "1" "--parents" id]
              {:dir repository-path})
        out (clojure.string/trim (:out res))
        ids (clojure.string/split out #"\s+")]
    (rest ids)))

(defn arcs-to-parents [id repository-path]
  (let [parent-ids  (get-git-parent-ids id repository-path)
        fun (fn [pid] {:child_id id :parent_id pid}) ]
    (map fun parent-ids )
    ))


;##############################################################################

(defn git-ls-tree-r [commit-id repository-path]
  (:out (system/exec!
          ["git" "ls-tree" "-r" commit-id]
          {:dir repository-path})))

(defn get-submodules
  "Returns a seq of maps each containing a :submodule_commit_id and :path key"
  [commit-id repository-path]
  (let [out (git-ls-tree-r commit-id repository-path)]
    (if (clojure.string/blank? out)
      []
      (->> out
           (#(split % #"\n"))
           (map #(split % #"\s+"))
           (filter #(= "commit" (nth % 1)))
           (map #(hash-map :submodule_commit_id (nth % 2) :path (nth % 3)))))))



;#### debug ###################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)

