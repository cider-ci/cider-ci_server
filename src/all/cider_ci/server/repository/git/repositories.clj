; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.


(ns cider-ci.server.repository.git.repositories
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer [keyword str]])

  (:import
    [java.io File]
    )
  (:require
    [cider-ci.server.repository.shared :refer [repository-fs-path]]
    [cider-ci.utils.config :as config :refer [get-config get-db-spec]]


    [cider-ci.utils.fs :as ci-fs]
    [cider-ci.utils.system :as system]
    [clojure.core.memoize :as memo]
    [clojure.string :as string :refer [blank? split trim]]

    [clojure.tools.logging :as logging]
    [logbug.debug :as debug]
    [logbug.catcher :as catcher]

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

(defn get-path-contents
  "Returns contents of the specified path for the git reference id
  and the given repository it exits. Throws an ExceptionInfo with {:status 404}
  if the path doesn't exist. Throws an ExceptionInfo with {:status 500}
  otherwise."
  [repository id file-path]
  (let [git-dir-path (repository-fs-path repository)]
    (let [wrapped-exec (system/async-exec
                         ["git" "show" (str id ":" file-path)]
                         {:dir git-dir-path})
          realized-exec (-> wrapped-exec deref :exec deref)]
      (logging/debug 'realized-exec realized-exec)
      (def ^:dynamic *realized-exec* realized-exec)
      (cond
        (= 0 (:exit realized-exec)) (:out realized-exec)
        ; throw ex-info with 404 if the file doesn't exist in the commit
        (and (= 128 (:exit realized-exec))
             (re-matches #"(?si).*Path.*does not exist in.*"
                         (or (:err realized-exec) "")))
        (throw (ex-info (:err realized-exec)
                        {:status 404}
                        (-> wrapped-exec deref :exception)))
        ; throw ex-info with 500 other cases
        :else (throw
                (ex-info "Error for get-path-contents"
                         {:status 500
                          :wrapped-exec @wrapped-exec
                          :realized-exec realized-exec}
                         (-> wrapped-exec deref :exception)))))))

(defn- ls-tree_unmemoized [repository id include-regex exclude-regex]
  (catcher/with-logging {}
    (->> (-> (system/exec!
               ["git" "ls-tree" "-r" "--name-only" id]
               {:dir (repository-fs-path repository)})
             :out
             (split #"\n"))
         (map trim)
         (filter #(and include-regex
                       (not (clojure.string/blank? include-regex))
                       (re-find (re-pattern include-regex) %)))
         (filter #(or (not exclude-regex)
                      (clojure.string/blank? exclude-regex)
                      (not (re-find (re-pattern exclude-regex) %)))))))

(def ls-tree
  (memo/lru ls-tree_unmemoized :lru/threshold 128))

; to disable caching temporarily:
;(def ls-tree_unmemoized ls-tree_unmemoized)


;#### debug ###################################################################
;(debug/debug-ns *ns*)
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
