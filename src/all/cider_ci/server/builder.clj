; Copyright © 2013 - 2017 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.server.builder
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer [keyword str]])
  (:require
    [cider-ci.server.builder.jobs :as jobs]
    [cider-ci.server.builder.project-configuration]
    ))

(def create-job jobs/create)

(defn available-jobs [tree-id]
  (jobs/available-jobs tree-id))

(def project-configuration
  cider-ci.server.builder.project-configuration/get-project-configuration)
