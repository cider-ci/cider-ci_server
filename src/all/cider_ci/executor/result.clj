; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.executor.result
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer :all])
  (:import
    [java.io File]
    [java.util UUID]
    [org.apache.commons.exec ExecuteWatchdog]
    )
  (:require
    [logbug.debug :as debug]
    [logbug.catcher :as catcher]
    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [me.raynes.fs :as fs]
    [clojure.data.json :as json]
    ))



(defn file-path-to-result [working-dir]
  (->
    (clojure.java.io/file working-dir)
    (fs/absolute)
    (fs/normalized)
    (str "/result.json")))

(defn try-read-and-merge [working-dir params-atom]
  (catcher/snatch {}
    (when (fs/exists? (file-path-to-result working-dir))
      (let [json-data (-> (file-path-to-result working-dir)
                          slurp
                          json/read-str)]
        (swap! params-atom
               #(assoc %1 :result %2)
               json-data))))
  params-atom)


;### Debug ####################################################################
;(debug/debug-ns *ns*)
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
