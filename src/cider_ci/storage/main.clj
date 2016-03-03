; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.


(ns cider-ci.storage.main
  (:gen-class)
  (:require
    [cider-ci.self]
    [cider-ci.storage.shared :as shared]
    [cider-ci.storage.sweeper :as sweeper]
    [cider-ci.storage.web :as web]
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


(defn -main [& args]
  (catcher/snatch
    {:level :fatal
     :throwable Throwable
     :return-fn #(System/exit -1)}
    (cider-ci.utils.app/init web/build-main-handler)
    (create-dirs (-> (get-config) :services :storage :stores))
    (sweeper/initialize (-> (get-config) :services :storage :stores))))

;### Debug ####################################################################
;(debug/debug-ns *ns*)
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)


