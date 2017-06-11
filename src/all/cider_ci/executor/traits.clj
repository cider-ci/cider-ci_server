; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.
;

(ns cider-ci.executor.traits
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer :all])
  (:require
    [cider-ci.executor.utils.tags :refer :all]
    [cider-ci.utils.config :as config :refer [get-config]]
    [cider-ci.utils.daemon :as daemon :refer [defdaemon]]
    [cider-ci.utils.fs :refer :all]
    [cider-ci.utils.core :refer :all]
    [clojure.java.io :as io]
    [clojure.set :refer [union]]
    [clojure.string :refer [blank? lower-case split trim]]

    [clojure.tools.logging :as logging]
    [logbug.debug :as debug]
    )
  (:import
    [org.apache.commons.lang3 SystemUtils]
    )
  )


(defonce ^:private traits (atom (sorted-set)))

(defn get-traits [] @traits)


;;; default traits ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn config-traits []
  #{(:hostname (get-config))
    (:name (get-config))})

(defn system-traits []
  #{(SystemUtils/OS_NAME)
    (str (SystemUtils/OS_NAME) " " (SystemUtils/OS_VERSION))})

(defn os-default-traits []
  (case (System/getProperty "os.name")
    "Linux" #{"Bash"}
    "Mac OS X" #{"Bash"}
    #{}))

(defn add-traits [coll xs]
  (apply conj coll xs))

(defn default-traits []
  (->> (-> @traits
           (add-traits (config-traits))
           (add-traits (os-default-traits))
           (add-traits (system-traits)))
       (map str)
       (map clojure.string/trim)
       (filter (complement clojure.string/blank?))
       (apply sorted-set)))

;;; traits-files traits ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn traits-files-traits []
  (->> (:traits_files (get-config))
       (read-tags-from-yaml-files)))

;;; update/reset ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- reset-traits []
  (let [new-traits (clojure.set/union
                     (default-traits)
                     (traits-files-traits))]
    (when-not (= new-traits @traits)
      (reset! traits new-traits)
      (logging/info "traits changed to " @traits))))


(defdaemon "reset-traits" 1 (reset-traits))

(defn initialize [] (start-reset-traits))

;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
