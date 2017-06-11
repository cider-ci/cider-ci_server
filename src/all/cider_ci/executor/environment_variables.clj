; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.
(ns cider-ci.executor.environment-variables
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer :all])
  (:require
    [cider-ci.executor.scripts.exec.shared :refer :all]
    [cider-ci.utils.core :refer :all]
    [selmer.parser]

    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.debug :as debug]
    ))


(defn- stringify [mp]
  (map
    (fn [[k v]] [(to-cistr k) (to-cistr v)])
    mp))

(defn- remove-nil-values [mp]
  (filter
    (fn [[k v]] (not= nil v))
    mp))

(defn- upper-case-keys [mp]
  (map
    (fn [[k v]] [(clojure.string/upper-case k) v])
    mp))

;##############################################################################

;(selmer.parser/render "Hello {{NAME}}!" (clojure.walk/keywordize-keys {"NAME" "Yogthos"}))

(defn- to-elmer-params [mp]
  (->> mp
       (into {})
       clojure.walk/keywordize-keys))

(defn- process-templates [mp]
  (let [params (to-elmer-params mp)
        new-mp (map
                 (fn [[k v]]
                   [k (selmer.parser/render v params)])
                 mp)]
    (if (not= mp new-mp)
      (process-templates new-mp)
      mp)))

;##############################################################################

(defn prepare [params]
  (->> (merge { }
              {:CIDER_CI_WORKING_DIR (working-dir params)
               :CIDER_CI true
               :CONTINUOUS_INTEGRATION true}
              (:ports params)
              (:environment_variables params))
       remove-nil-values
       stringify
       upper-case-keys
       (#(if (:template_environment_variables params)
           (process-templates %) %))
       (into {})))


;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(remove-ns (symbol (str *ns*)))
;(debug/debug-ns *ns*)
;(debug/re-apply-last-argument #'prepare)
