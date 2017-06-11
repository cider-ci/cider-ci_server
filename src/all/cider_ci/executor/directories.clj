; Copyright © 2013 - 2017 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.executor.directories
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer :all])
  (:require
    [cider-ci.utils.config :as config :refer [get-config merge-into-conf]]

    [camel-snake-kebab.core :refer [->kebab-case]]
    [me.raynes.fs :as clj-fs]
    )
  (:import
    [java.io File]
    )
  )

(defn initialize-and-configure-dir [dir & {:keys [clean] :or {clean false}}]
  (let [dirstr (name dir)
        dirkw (keyword dir)
        dirname (->kebab-case dirstr)]
    (when-not (get (get-config) dirkw)
      (merge-into-conf {dirkw (str (-> (get-config) :tmp_dir)
                                   File/separator
                                   dirname)}))
    (let [dir  (-> (get-config) (get dirkw))]
      (when clean (clj-fs/delete-dir dir))
      (clj-fs/mkdir dir))))



(defn initialize []
  (initialize-and-configure-dir :repositories_dir)
  (initialize-and-configure-dir :working_dir :clean true))
