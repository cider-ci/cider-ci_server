; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.executor.git.submodules
  (:refer-clojure :exclude [update str keyword])
  (:require [cider-ci.utils.core :refer :all])
  (:require
    [cider-ci.executor.git.repository :as repository]
    [cider-ci.utils.config :as config :refer [get-config]]
    [logbug.debug :as debug]
    [cider-ci.utils.system :as system]
    [clj-logging-config.log4j :as logging-config]
    [clojure-ini.core :as ini]
    [clojure.tools.logging :as logging]
    [clojure.string :as string]
    [me.raynes.fs :as fs]

    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.debug :as debug :refer [I> I>> identity-with-logging]]
    ))

(defn filter-sha1-chars [s]
  (I> identity-with-logging
      s
      (string/split #"")
      ((fn [s] (filter #(re-matches #"[a-f0-9]" %) s)))
      string/join))

(defn get-commit-id-of-submodule [dir relative-submodule-path]
  (I> identity-with-logging
      (system/exec!
        ["git" "submodule" "status"  relative-submodule-path]
        {:dir dir :timeout "1 Minute"  })
      :out
      (#(if (string/blank? %)
          (throw (ex-info (str "Not git commit for submodule "
                               dir " rel path " relative-submodule-path
                               " was found!" ) {:out %} ))
          %))
      string/trim
      (string/split #"\s+")
      first
      filter-sha1-chars))

(defn gitmodules-conf [dir]
  (let [path (str dir "/.gitmodules")]
    (if (fs/exists? path)
      (ini/read-ini path :keywordize? true)
      {})))

;### path matcher ##############################################################

(defn- matches-path? [path pattern]
  (re-find (re-pattern pattern) path))


(defn- include-match-includes? [path clone-options]
  (if-let [include-match (:include_match clone-options)]
    (matches-path? path include-match)
    true))

(defn- exclude-match-not-excludes? [path clone-options]
  (if-let [exclude-match (:exclude_match clone-options)]
    (not (matches-path? path exclude-match))
    true))

(defn- clone-path? [path clone-options]
  (and (include-match-includes? path clone-options)
       (exclude-match-not-excludes? path clone-options)))

;### update ####################################################################

(declare update)

(defn update-submodule [dir path bare-dir clone-options git-proxies]
  (let [submodule-dir (str dir (java.io.File/separator) path)]
    (update submodule-dir clone-options git-proxies)))

(defn update [dir clone-options git-proxies]
  (doseq [[_ submodule] (gitmodules-conf dir)]
    (logging/debug 'SUBMODULE submodule)
    (let [path (:path submodule)]
      (when (clone-path? path clone-options)
        (let [submodule-dir (str dir (java.io.File/separator) path)
              url (:url submodule)
              commit-id (get-commit-id-of-submodule dir path)
              proxy-url ((-> commit-id str keyword) git-proxies)
              bare-dir (repository/serialized-initialize-or-update-if-required url proxy-url commit-id)]
          (repository/clone-with-update-to-dir url proxy-url commit-id submodule-dir)
          (update-submodule dir path bare-dir clone-options git-proxies))))))

;### Debug #####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
