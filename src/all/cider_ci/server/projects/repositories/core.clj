; Copyright © 2013 - 2018 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.
 
(ns cider-ci.server.projects.repositories.core
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer [keyword str presence]])
  (:require
    [cider-ci.server.paths :refer [path]]
    [cider-ci.server.projects.repositories :as repositories]
    [cider-ci.utils.rdbms :as ds]

    [cider-ci.utils.git-gpg :as git-gpg]
    [cider-ci.utils.honeysql :as sql]
    [cider-ci.utils.jdbc :as utils.jdbc]

    [clj-time.coerce]
    [clojure.java.jdbc :as jdbc]
    [compojure.core :as cpj]

    [clojure.tools.logging :as logging]
    [logbug.debug :as debug]
    )
  (:import 
    [java.io File]
    [org.eclipse.jgit.api Git]
    [org.eclipse.jgit.lib Repository ObjectId]
    [org.eclipse.jgit.revwalk RevCommit RevWalk RevTree]
    [org.eclipse.jgit.storage.file FileRepositoryBuilder]
    [org.eclipse.jgit.treewalk TreeWalk]
    [org.eclipse.jgit.treewalk.filter PathFilter]
    [org.eclipse.jgit.util RawParseUtils]
    ))


(defn path-content [repository commit-id path]
  "Returns the contents as a byte array of a file in a revision given by commit-id.
  Returns nil if the file is not found. Throws a MissingObjectException
  if the object can not be resolved. Throws a IncorrectObjectTypeException if e.g. 
  a tree-id is given instead of a commit-d."
  (let [object-id (.resolve repository (str commit-id "^{commit}"))
        revwalk (RevWalk. repository)
        revcmt (.parseCommit revwalk object-id)
        revtree (.getTree revcmt)]
    (some->> 
      (some-> (TreeWalk/forPath repository path revtree)
              (.getObjectId 0))
      (.open repository)
      .getBytes)))

;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
