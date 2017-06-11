; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.executor.trials.templates
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer :all])
  (:require
    [cider-ci.executor.environment-variables]
    [cider-ci.executor.trials.helper :refer :all]
    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug]
    [logbug.thrown :as thrown]
    [selmer.parser :refer [render]]
    [clojure.string :refer [join]]
    )
  (:import
    [java.io File]
    ))



(defn render-string-template [s env-vars]
  (let [env-vars (clojure.walk/keywordize-keys env-vars)]
    (let [rendered (render s env-vars)]
      (if (not= rendered s)
        (render-string-template rendered env-vars)
        rendered))))

(defn- render-file-template
  ([src dest env-vars template]
   (try (let [template (slurp src)
              rendered (render-string-template template env-vars)]
          (spit dest rendered))
        (catch java.io.FileNotFoundException e
          (throw (ex-info
                   (str "The template for " template " does not to exist.")
                   {:template template}
                   e))))))

(defn- render-template
  ([template params]
   (let [working-dir (:working_dir params)
         env-vars (-> params
                      cider-ci.executor.environment-variables/prepare)
         src (join (File/separator) (flatten [working-dir (:src template)]))
         dest (join (File/separator) (flatten [working-dir (:dest template)]))]
     (render-file-template src dest env-vars template)
     )))

(defn render-templates [trial]
  (let [trial-params (-> trial :params-atom deref)]
    (doseq [template (:templates trial-params)]
      (render-template template trial-params))
    trial))

;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
