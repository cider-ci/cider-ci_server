; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.executor.git
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer :all])
  (:import
    [java.io File]
    )
  (:require
    [cider-ci.executor.git.repository :as repository]
    [cider-ci.executor.git.submodules :as submodules]
    [cider-ci.utils.config :as config :refer [get-config]]
    [cider-ci.utils.http :refer [build-server-url]]

    [clj-time.core :as time]
    [clj-uuid]
    [clojure.string :as string :refer [blank?]]
    [clojure.tools.logging :as logging]
    [me.raynes.fs :as fs]

    [clj-logging-config.log4j :as logging-config]
    [logbug.debug :as debug]
    [logbug.thrown :as thrown]
    )
  )


(defn- prepare-git-proxies [git-proxies]
  (->> git-proxies
       (map (fn [[commit-id path]]
              [(name commit-id) (build-server-url path)]))
       (into {})))


;### Core Git #################################################################

(defn prepare-and-create-working-dir [params]
  ;(throw (ex-info "Simulating exception during prepare-and-create-working-dir." {}))
  (let [working-dir-id (:trial_id params)
        commit-id (:git_commit_id params)
        git-proxies (:git-proxies params)
        repository-url (:git_url params)
        proxy-url ((-> commit-id str keyword) git-proxies)]
    (assert (not (blank? working-dir-id)))
    (assert (not (blank? commit-id)))
    (assert (not (blank? repository-url)))
    (let [working-dir (-> (str (:working_dir (get-config))
                               (File/separator) working-dir-id)
                          fs/absolute fs/normalized str)]
      (repository/clone-with-update-to-dir repository-url proxy-url commit-id working-dir)
      (when-let [clone-options (-> params :git_options :submodules)]
        (submodules/update working-dir clone-options git-proxies))
      working-dir)))

;### Debug #####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
