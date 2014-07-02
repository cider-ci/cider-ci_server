; Copyright (C) 2013, 2014 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.


(ns cider-ci.rm.git.repositories
  (:require 
    [clojure.tools.logging :as logging]
    ))


(defonce conf (atom {}))

(defn initialize [new-conf]
  (logging/info initialize [new-conf])
  (reset! conf new-conf))


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
  ;"Retuns the base path" 
  ;[]
  ;(:path @conf)
  "Returns the absolute path to the (git-)repository.
  Performs sanity checks on the path an throws exceptions in case."
  [_repository]
  (let [path (str (:path @conf)
                  "/" (str (canonic-id _repository)))]
    (if (re-matches #".+\/([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})"  path)
      path
      (throw (IllegalStateException. (str "Not a valid repository-path: " path "for args:" _repository))))))


