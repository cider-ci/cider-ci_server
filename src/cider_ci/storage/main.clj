; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.storage.main
  (:gen-class)
  (:require
    [cider-ci.storage.shared :as shared]
    [cider-ci.storage.sweeper :as sweeper]
    [cider-ci.storage.web :as web]
    [cider-ci.utils.config :as config :refer [get-config get-db-spec]]
    [logbug.debug :as debug]
    [cider-ci.utils.map :refer [deep-merge]]
    [cider-ci.utils.nrepl :as nrepl]
    [cider-ci.utils.rdbms :as rdbms]
    [logbug.catcher :as catcher]
    [logbug.thrown]
    [clojure.tools.logging :as logging]
    [me.raynes.fs :as fsutils]
    [pg-types.all]
    ))

(defn create-dirs [stores]
  (doseq [store stores]
    (let [directory-path (:file_path store)]
      (catcher/snatch {}
        (logging/debug "mkdirs " directory-path)
        (fsutils/mkdirs directory-path)))))

(defn -main [& args]
  (catcher/with-logging {}
    (logbug.thrown/reset-ns-filter-regex #".*cider.ci.*")
    (config/initialize {:overrides {:service :storage}})
    (rdbms/initialize (get-db-spec :repository))
    (nrepl/initialize (-> (get-config) :services :storage :nrepl))
    (create-dirs (-> (get-config) :services :storage :stores))
    (web/initialize)
    (sweeper/initialize (-> (get-config) :services :storage :stores))))


;### Debug ####################################################################
;(debug/debug-ns *ns*)
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)


