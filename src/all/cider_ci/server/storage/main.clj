; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.


(ns cider-ci.server.storage.main
  (:gen-class)
  (:require
    [cider-ci.server.storage.shared :as shared]
    [cider-ci.server.storage.sweeper :as sweeper]
    [cider-ci.server.storage.web :as web]
    ;[cider-ci.utils.app]
    [cider-ci.utils.config :as config :refer [get-config get-db-spec]]

    [camel-snake-kebab.core :refer [->snake_case]]
    [me.raynes.fs :as fsutils]

    [logbug.catcher :as catcher]
    [logbug.thrown]
    [clojure.tools.logging :as logging]
    )
  (:import
    [java.io File]))

(defn create-dirs [stores]
  (doseq [store stores]
    (let [directory-path (:file_path store)]
      (catcher/snatch {}
        (logging/debug "mkdirs " directory-path)
        (fsutils/mkdirs directory-path)))))

(defn initialize []
  (let [stores (->> ["trial-attachments", "tree-attachments"]
                    (map (fn [name]
                           {:url_path_prefix (str "/" name)
                            :file_path (str (:attachments-path (get-config)) File/separator name)
                            :db_table (->snake_case name)})))]
    (create-dirs stores)
    (sweeper/initialize stores)))

(defn -main [& args]
  (catcher/snatch
    {:level :fatal
     :throwable Throwable
     :return-fn #(System/exit -1)}
    ;(cider-ci.utils.app/init web/build-main-handler)
    (initialize)))

;### Debug ####################################################################
;(debug/debug-ns *ns*)
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)


