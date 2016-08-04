; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.utils.url.shared
  (:require

    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug :refer [I> I>> identity-with-logging]]
    [logbug.thrown :as thrown]
    ))

(defn host-port-dissect [host-port]
  (if-not (string? host-port)
    {:host nil
     :port nil}
    (if-let [match (re-matches #"(.*):(\d+)" host-port)]
      {:host (nth match 1)
       :port (nth match 2)}
      {:host host-port
       :port nil})))

(defn path-dissect [path]
  (if-not path
    {:project_name nil
     :project_namespace nil}
    (let [segments (clojure.string/split path #"\/")
          project-name (last segments)
          project-namespace (-> segments reverse (nth 1 nil))]
      {:project_name (and project-name
                          (if-let [match (re-matches #"(.*)\.git$" project-name)]
                            (second match)
                            project-name))
       :project_namespace project-namespace
       :context (->> segments reverse (drop 2) reverse (clojure.string/join "/"))})))

(defn auth-dissect [auth]
  (if-not (string? auth)
    {:username nil
     :password nil}
    (let [match (re-matches #"([^:]+)(:([^@]+))?@" auth)]
      (if (string? match)
        {:username match
         :password nil}
        {:username (nth match 1)
         :password (nth match 3)}))))
