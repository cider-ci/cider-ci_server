; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.


(ns cider-ci.server.storage.main
  (:gen-class)
  (:require
    [cider-ci.server.storage.shared :as shared]
    [cider-ci.server.storage.sweeper :as sweeper]
    [cider-ci.server.storage.web :as web]
    [cider-ci.utils.app]
    [cider-ci.utils.config :as config :refer [get-config get-db-spec]]

    [me.raynes.fs :as fsutils]

    [logbug.catcher :as catcher]
    [logbug.thrown]
    [clojure.tools.logging :as logging]
    ))

(defn create-dirs [stores]
  (doseq [store stores]
    (let [directory-path (:file_path store)]
      (catcher/snatch {}
        (logging/debug "mkdirs " directory-path)
        (fsutils/mkdirs directory-path)))))


(defn initialize []
  (create-dirs (-> (get-config) :services :server :stores))
  (sweeper/initialize (-> (get-config) :services :server :stores)))

(defn -main [& args]
  (catcher/snatch
    {:level :fatal
     :throwable Throwable
     :return-fn #(System/exit -1)}
    (cider-ci.utils.app/init web/build-main-handler)
    (initialize)))

;### Debug ####################################################################
;(debug/debug-ns *ns*)
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)


