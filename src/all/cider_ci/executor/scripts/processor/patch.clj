; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.executor.scripts.processor.patch
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer :all])
  (:require
    [cider-ci.executor.scripts.exec :as exec]
    [cider-ci.executor.trials.helper :as trial]
    [cider-ci.executor.utils.state :refer [pending? executing? finished?]]
    [cider-ci.utils.core :refer :all]
    [clj-logging-config.log4j :as logging-config]
    [clj-time.core :as time]
    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug]
    ))

(defn- eval-patch [trial old-state new-state]
  (catcher/snatch {}
    (trial/send-patch-via-agent trial {:scripts {(:key new-state) new-state}})
    ))

(defn add-watchers [trial]
  (doseq [script-atom (trial/get-scripts-atoms trial)]
    (add-watch script-atom
               :patch
               (fn [_ script-atom old-state new-state]
                 (eval-patch trial old-state new-state)))))

(defn remove-watchers [trial]
  (doseq [script-atom (trial/get-scripts-atoms trial)]
    (remove-watch script-atom :patch)))

;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
;
;(logging-config/set-logger! :level :debug)
;(debug/wrap-with-log-debug #'eval-patch)
