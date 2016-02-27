; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.


(ns cider-ci.repository.git.commits
  (:refer-clojure :exclude [get])
  (:require
    [clj-commons-exec :as commons-exec]
    [clj-logging-config.log4j :as logging-config]
    [clj-time.coerce :as time-coerce]
    [clj-time.core :as time-core]
    [clj-time.format :as time-format]
    [clojure.tools.logging :as logging]
    [cider-ci.utils.system :as system]
    [clojure.string :as string :refer [split]]
    ))

;##############################################################################

(defn- trim-line [lines n]
  (clojure.string/trim (nth lines n)))

(defn- parse-time [ts]
  (time-coerce/to-sql-time (time-format/parse (time-format/formatters :rfc822) ts)))

(defn get [id repository-path]
  (logging/debug "get-info " id repository-path)
  (let [res (system/exec
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
  (let [res (system/exec
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
  (:out (system/exec-with-success-or-throw
          ["git" "ls-tree" "-r" commit-id]
          {:dir repository-path})))

;(git-ls-tree-r "f03949538e290bc4e855c8f05fdac190813a9362" "./tmp/repositories/https-github-com-zhdk-leihs-git_873beeef-a3a1-5ae9-8a5c-b3f9870c1b54")

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

;(get-submodules "5f0430a5c7d3d399ad717c9d347b7a57d56a69c9" "./tmp/repositories/http-localhost-8888-cider-ci-demo-project-bash_e62bc7e0-81da-5cdb-bb04-58f429e9f7c1")
;(type (:out (deref (commons-exec/sh ["git" "ls-tree" "-r" "5f0430a5c7d3d399ad717c9d347b7a57d56a69c9"] {:dir "./tmp/repositories/http-localhost-8888-cider-ci-demo-project-bash_e62bc7e0-81da-5cdb-bb04-58f429e9f7c1" }))))
;(get-submodules "f03949538e290bc4e855c8f05fdac190813a9362" "./tmp/repositories/https-github-com-zhdk-leihs-git_873beeef-a3a1-5ae9-8a5c-b3f9870c1b54")
