; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.repository
  (:require
    [cider-ci.repository.project-configuration :as project-configuration]

    [clj-logging-config.log4j :as logging-config]
    [logbug.debug :as debug]
    [logbug.thrown :as thrown]
    ))

(defn project-configuration [tree-or-commit-id]
  "Returns the project configuration according to cider-ci.yml etc.
  The include statements have been evaluated at this point."
  (project-configuration/build-project-configuration tree-or-commit-id))

