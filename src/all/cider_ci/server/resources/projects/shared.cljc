; Copyright © 2013 - 2018 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.server.resources.projects.shared
  (:refer-clojure :exclude [str keyword])
  (:require
    [cider-ci.utils.core :refer [keyword str presence]]))

(def default-project-params
  {:branch_trigger_enabled false
   :cron_trigger_enabled false
   :id ""
   :name ""
   :public_view_permission false})
