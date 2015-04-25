; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.repository.git.branches
  (:require 
    [drtom.logbug.thrown :as thrown]
    [cider-ci.utils.system :as system]
    [clojure.tools.logging :as logging]
    ))

(defn get-branches
  "Returns a sequence of (git) branches, each branch has
  the properities :branch_name and :current_commit_id"
  [repository-path]
  (let [res (system/exec
              ["git" "branch" "--no-abbrev" "--no-color" "-v"] 
              {:watchdog (* 1 60 1000), :dir repository-path, :env {"TERM" "VT-100"}})
        out (:out res)
        lines (clojure.string/split out #"\n")
        branches (map (fn [line]
                        (let [[_ branch-name current-commit-id] 
                              (re-find #"^?\s+(\S+)\s+(\S+)\s+(.*)$" line)]
                          {:name branch-name 
                           :current_commit_id current-commit-id}))
                      lines)]
    branches))

  ;(get-branches "/Users/thomas/Programming/ROR/cider-ci_server-tb/repositories/f81e51fa-b83e-4fba-8f2f-d3f0d71ccc4f")





