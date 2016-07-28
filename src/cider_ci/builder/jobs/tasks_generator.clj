; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.builder.jobs.tasks-generator
  (:require
    [cider-ci.utils.core :refer :all]
    [cider-ci.utils.http :as http]

    [clojure.core.memoize :as memo]

    [clj-logging-config.log4j :as logging-config]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug :refer [I> I>> identity-with-logging]]
    [clojure.tools.logging :as logging]
    ))

(defn- file-name-to-task [file-name]
  [file-name {:environment_variables
              {:CIDER_CI_TASK_FILE file-name}}])

(defn- get-file-list_unmemoized [git-ref generate-spec]
  (let [url (http/build-service-url
              :repository "/ls-tree"
              (merge generate-spec
                     {:git_ref git-ref  }))]
    (-> url
        (http/get {:socket-timeout 10000
                   :conn-timeout 10000
                   :as :json})
        :body)))

(def ^:private get-file-list_memoized
  (memo/lru get-file-list_unmemoized :lru/threshold 32))

(def ^:dynamic *caching-enabled* true)

(defn get-file-list [& args]
  (apply (if *caching-enabled*
           get-file-list_memoized
           get-file-list_unmemoized)
         args))

(defn generate-tasks-for-this-context [git-ref context]
  (if-let [generate-spec (:generate_tasks context)]
    (let [file-list (get-file-list git-ref generate-spec)
          generated-tasks (->> file-list
                               (map file-name-to-task)
                               (into {})
                               clojure.walk/keywordize-keys)
          tasks (if-let [existing-tasks (:tasks context)]
                  (cond (map? existing-tasks) (deep-merge
                                                generated-tasks existing-tasks)
                        :else (throw (IllegalStateException.
                                       (str "tasks must be a map"
                                            " to be merged with generated-tasks"))))
                  generated-tasks)]
      (-> context
          (assoc :tasks tasks)
          (dissoc :generate_tasks)))
    context))

(defn generate-tasks [git-ref context]
  (as-> context context
    (generate-tasks-for-this-context git-ref context)
    (if-let [ctxs (:contexts context)]
      (assoc context :contexts
             (->> ctxs
                  (map (fn [[k ctx]] [k (generate-tasks git-ref ctx)]))
                  (into {})))
      context)))

(defn generate [tree-id job-spec]
  (let [context (:context job-spec)]
    (assoc job-spec :context (generate-tasks tree-id context))))

;### Debug ####################################################################
;(debug/debug-ns 'cider-ci.utils.http)
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/wrap-with-log-debug #'create)
;(debug/debug-ns *ns*)
