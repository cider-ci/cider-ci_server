; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.executor.scripts.exec.shared
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer :all])
  (:import
    [java.io File]
    )
  (:require
    [cider-ci.utils.config :as config :refer [get-config]]
    [cider-ci.utils.core :refer :all]

    [pandect.algo.sha1 :refer [sha1]]
    [clj-time.core :as time]
    [clojure.set :refer [difference union]]
    [clojure.string :as string :refer [split trim]]

    [clojure.tools.logging :as logging]
    [clj-logging-config.log4j :as logging-config]
    [logbug.catcher :refer [snatch]]
    [logbug.debug :as debug]
    [logbug.thrown :as thrown]
    ))

(defn expired? [script-atom & ds]
  (let [timeout (:timeout @script-atom)
        started-at (:started_at @script-atom)]
    (time/after? (time/now)
                 (apply time/plus
                        (concat [started-at (time/seconds timeout)] ds)))))

(defn add-issue [script-atom issue]
  (snatch
    {}
    (assert (not (clojure.string/blank? (:title issue)))
            (str "An issue must have a title. " issue))
    (assert (not (clojure.string/blank? (:description issue)))
            (str "An issue must have a title. " issue))
    (swap! script-atom
           (fn [params issue]
             (let [k (or (:key issue) (sha1 (str issue)))]
               (deep-merge params
                           {:issues {k (deep-merge
                                         issue
                                         {:created_at (time/now)})
                                     }})))

           issue)))

(defn merge-params [script-atom add-params]
  (swap! script-atom
         (fn [params add-params]
           (merge params add-params))
         add-params))

(defn working-dir [params]
  (.getAbsolutePath (File. (:working_dir params))))

;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(remove-ns (symbol (str *ns*)))
;(debug/debug-ns *ns*)
