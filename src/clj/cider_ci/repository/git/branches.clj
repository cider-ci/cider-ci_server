; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.repository.git.branches
  (:require
    [logbug.thrown :as thrown]
    [cider-ci.utils.system :as system]
    [clojure.tools.logging :as logging]
    ))

(defn get-branches
  "Returns a sequence of (git) branches, each branch has
  the properities :branch_name and :current_commit_id"
  [repository-path]
  (let [res (system/exec!
              ["git" "branch" "--no-abbrev" "--no-color" "-v"]
              {:timeout "1 Minute", :dir repository-path, :env {"TERM" "VT-100"}})
        out (:out res)
        lines (clojure.string/split out #"\n")
        branches (map (fn [line]
                        (let [[_ branch-name current-commit-id]
                              (re-find #"^?\s+(\S+)\s+(\S+)\s+(.*)$" line)]
                          {:name branch-name
                           :current_commit_id current-commit-id}))
                      lines)]
    branches))
