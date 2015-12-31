; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.


(ns cider-ci.repository.git.repositories
  (:import
    [java.io File]
    )
  (:require
    [cider-ci.utils.config :as config :refer [get-config get-db-spec]]
    [logbug.debug :as debug]
    [cider-ci.utils.fs :as ci-fs]
    [cider-ci.utils.system :as system]
    [logbug.catcher :as catcher]
    [clojure.string :as string :refer [blank? split trim]]
    [clojure.tools.logging :as logging]
    ))


(defn canonic-id
  "Returns the id as a java.lang.UUID of the repository.  Input can either be a
  String, a PersistentHashMap (representing a db row), or a java.lang.UUID."
  [_repository]
  ;(logging/debug repository-canonic-id [_repository])
  (case (.getName (type _repository))
    "clojure.lang.PersistentHashMap" (canonic-id (:id (clojure.walk/keywordize-keys _repository)))
    "clojure.lang.PersistentArrayMap" (canonic-id (:id (clojure.walk/keywordize-keys _repository)))
    "java.lang.String" (java.util.UUID/fromString _repository)
    "java.util.UUID" _repository))


(defn path
  "Returns the absolute path to the (git-)repository."
  [repository]
  (let [path  (-> (get-config) :services :repository :repositories :path)
        git-url (:git_url repository)]
    (assert (not (blank? path)))
    (assert (not (blank? git-url)))
    (str path (File/separator) (ci-fs/path-proof git-url))))


(defn get-path-contents
  [repository id file-path]
  (let [git-dir-path (path repository)
        res (system/exec
              ["git" "show" (str id ":" file-path)]
              {:dir git-dir-path})]
    (if (= (:exit res) 0) (:out res)
      (throw (ex-info (:err res)
                      res )))))


(defn ls-tree [repository id include-regex exclude-regex]
  (catcher/with-logging {}
    (->> (-> (system/exec-with-success-or-throw
               ["git" "ls-tree" "-r" "--name-only" id]
               {:dir (path repository)})
             :out
             (split #"\n"))
         (map trim)
         (filter #(and include-regex
                       (re-find (re-pattern include-regex) %)))
         (filter #(or (not exclude-regex)
                      (not (re-find (re-pattern exclude-regex) %)))))))

;#### debug ###################################################################
;(debug/debug-ns *ns*)
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
