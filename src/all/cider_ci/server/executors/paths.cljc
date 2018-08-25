; Copyright © 2013 - 2018 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.  

(ns cider-ci.server.executors.paths
  (:refer-clojure :exclude [str keyword])
  (:require
    [cider-ci.utils.core :refer [keyword str]]
    [bidi.verbose :refer [branch param leaf]]
    [bidi.bidi :as bidi :refer [path-for match-route]]
    [cider-ci.utils.url.query-params :refer [encode-query-params]]
    #?@(:clj
         [[clojure.tools.logging :as logging]
          [logbug.catcher :as catcher]
          [logbug.debug :as debug]
          [logbug.thrown :as thrown]
          ])))

(def paths
  (branch "/"
          (leaf "" :executors)
          (leaf "add" :executors-add)
          (branch ""
                  (param [#":?[a-z][a-z0-9-_]+" :executor-id])
                  (leaf "" :executor))))
