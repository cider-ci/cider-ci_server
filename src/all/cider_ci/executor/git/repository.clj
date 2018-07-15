; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.executor.git.repository
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer :all])
  (:import
    [java.io File]
    )
  (:require
    [cider-ci.utils.config :as config :refer [get-config]]
    [cider-ci.utils.fs :as ci-fs]
    [cider-ci.utils.system :as system]

    [clj-time.core :as time]
    [me.raynes.fs :as fs]

    [logbug.thrown :as thrown]
    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.debug :as debug :refer [I> I>> identity-with-logging]]
    [logbug.catcher :as catcher]

    ))


;### Repo path ################################################################

(defn- canonical-repository-path [repository-url]
  (let [absolute-repos-path (-> (get-config) :repositories_dir fs/absolute)
        path (str absolute-repos-path  (File/separator)
                  (ci-fs/path-proof repository-url))]
    (assert (> (count path) (count (str absolute-repos-path))))
    path))

;### Repositories #############################################################

(def repositories-atom (atom {}))

(defn- swap-in-repository [repositories url]
  (if (get repositories url)
    repositories
    (assoc repositories
           url
           {:repository-url url
            :repository-path (canonical-repository-path url)
            :exceptions []
            :created_at (time/now)
            :lock (Object. )
            })))

(defn- get-or-create-repository [repository-url]
  (or (get @repositories-atom repository-url)
      (get (swap! repositories-atom swap-in-repository repository-url)
           repository-url)))


;### Core Git #################################################################

(defn- git-fetch [path repository-url]
  (system/exec!
    ["git" "fetch" "--force" "--tags" "--prune" repository-url "+*:*"]
    {:dir path
     :add-env {"GIT_SSL_NO_VERIFY" "1"}
     :timeout "10 Minutes"}))

(defn- initialize-repo [repository-url proxy-url path]
  (catcher/snatch {:level :debug} (system/exec! ["rm" "-rf" path]))
  (system/exec! ["git" "init" "--bare" path])
  (git-fetch path (or proxy-url repository-url)))

(defn- repository-includes-commit? [path commit-id]
  (catcher/snatch
    {:return-expr false}
    (system/exec! ["git" "cat-file" "-t" commit-id]
                  {:dir path
                   :timeout "5 Seconds"
                   })
    (system/exec! ["git" "ls-tree" commit-id]
                  {:dir path
                   :timeout "5 Seconds"})
    true))

(defn- valid-git-repository? [repository-path]
  (and (fs/directory? repository-path)
       (catcher/snatch
         {:return-expr false :level :debug}
         (system/exec!
           ["git" "rev-parse" "--resolve-git-dir" "."]
           {:dir repository-path
            :timout "5 Seconds"}))))

(defn- initialize-or-update-if-required [repository proxy-url commit-id]
  (let [repository-path (:repository-path repository)
        repository-url (:repository-url repository)]
    (when-not (valid-git-repository? repository-path)
      (initialize-repo repository-url proxy-url repository-path))
    (loop [update-count 1]
      (when-not (repository-includes-commit? repository-path commit-id)
        (git-fetch repository-path (or proxy-url repository-url))
        (when-not (repository-includes-commit? repository-path commit-id)
          (when (<= update-count 3)
            (Thread/sleep 250)
            (recur (inc update-count))))))
    (when-not (repository-includes-commit? repository-path commit-id)
      (throw (ex-info "The git commit is not present."
                      {:repository-path repository-path
                       :commit-id commit-id})))))

(defn serialized-initialize-or-update-if-required
  "Returns the repository object. The referenced :repository-path
  inside is guaranteed to contain the commit-id."
  [repository-url proxy-url commit-id]
  (let [repository (get-or-create-repository repository-url)]
    (locking (:lock repository)
      (initialize-or-update-if-required repository proxy-url commit-id))
    repository))

(defn- clone-to-dir [repository commit-id dir]
  (let [repository-path (:repository-path repository)]
  (system/exec!
    ["git" "clone" "--shared" "--no-checkout" repository-path dir]
    {:timeout "5 Minutes"})
  (system/exec!
    ["git" "checkout" commit-id]
    {:dir dir :timeout "5 Minutes" })))

(defn reset-origin [repository-url working-dir]
  (system/exec!
    ["git" "remote" "remove" "origin"]
    {:dir working-dir})
  (system/exec!
    ["git" "remote" "add" "origin" repository-url]
    {:dir working-dir}))

(defn clone-with-update-to-dir
  "Clones creates a shallow clone in working-dir by referencing a local clone.
  Throws an exception if creating the clone failed."
  [repository-url proxy-url commit-id working-dir]
  (let [repository (serialized-initialize-or-update-if-required
                     repository-url proxy-url commit-id)]
    (if-not (:serialized_checkouts (get-config))
      (clone-to-dir repository commit-id working-dir)
      (locking (:lock repository)
        (clone-to-dir repository commit-id working-dir)))
    (reset-origin repository-url working-dir)))


;### Debug #####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
