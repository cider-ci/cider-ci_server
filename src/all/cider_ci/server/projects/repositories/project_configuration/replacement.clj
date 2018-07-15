; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.server.projects.repositories.project-configuration.replacement
  (:require
    [cider-ci.server.projects.repositories.project-configuration.shared :refer :all]

    [clojure.data.json :as json]

    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug]
    ))

(defn check-read-and-repace-spec! [read-and-replace-spec spec]
  (when-not (= 1 (-> spec keys count))
    (throw (IllegalStateException.
             (str "The map containing :read_and_replace_with "
                  " must have exactly one key. But "
                  (json/write-str spec) " contains "
                  (-> spec keys count) " keys." ))))
  (when-not (or (string? read-and-replace-spec)
                (map? read-and-replace-spec))
    (throw (IllegalStateException.
             (str "The value of :read_and_replace_with must be a string
                  or a map. But it is "
                  (json/write-str read-and-replace-spec)".")))))

(defn read-and-replace [git-ref-id spec]
  (if-let [read-and-replace-spec (:read_and_replace_with spec)]
    (do (check-read-and-repace-spec! read-and-replace-spec spec)
        (let [path (if (string? read-and-replace-spec)
                     read-and-replace-spec
                     (:path read-and-replace-spec))
              submodules (or (:submodule read-and-replace-spec) [])]
          (when (clojure.string/blank? path)
            (throw (IllegalStateException.
                     (str "The path value of :read_and_replace_with "
                          " must not be blank but it is in "
                          (json/write-str spec) "."))))
          (-> (get-content git-ref-id path submodules)
              clojure.string/trim)))
    spec))

;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
