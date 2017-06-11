; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.
;

(ns cider-ci.executor.utils.tags
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer :all])
  (:require
    [clj-logging-config.log4j :as logging-config]
    [yaml.core :as yaml]
    [clojure.java.io :as io]
    [clojure.set :refer [union]]
    [clojure.string :refer [blank? lower-case split trim]]
    [clojure.tools.logging :as logging]
    [clojure.tools.logging :as logging]
    [logbug.debug :as debug]
    [me.raynes.fs :as clj-fs]
    ))
; TODO change ansible inventory !

(defn- read-and-merge-tags
  ([]
   (sorted-set))
  ([tag-set file]
   (if (.exists file)
     (try (->> (slurp file)
               yaml/parse-string
               (map str)
               (map clojure.string/trim)
               (remove blank?)
               (apply sorted-set)
               (union tag-set))
          (catch Exception e
            (logging/warn "Failed to read tags from " file " because " e)
            tag-set))
     tag-set)))

(defn read-tags-from-yaml-files [filenames]
 (reduce read-and-merge-tags (sorted-set) (map clj-fs/absolute filenames)))

;(read-tags-from-yaml-files ["config/traits.yml"])

;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns 'cider-ci.auth.http-basic)
;(debug/debug-ns *ns*)

