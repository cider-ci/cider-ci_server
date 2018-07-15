; Copyright © 2013 - 2018 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.
 
(ns cider-ci.server.projects.scratch
  (:refer-clojure :exclude [str keyword resolve])
  (:require [cider-ci.utils.core :refer [keyword str presence]])
  (:require
    [cider-ci.server.projects.core :as projects-core]
    [cider-ci.server.projects.repositories.core :as repositories-core]

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
    )
  
  )

(some-> @projects-core/projects*
    (get "test")
    :repository
    (repositories-core/path-content "cfabd9e2832f5c7678ab1f3b85fc160a816a7a42" "README.md")
    String.
    )

