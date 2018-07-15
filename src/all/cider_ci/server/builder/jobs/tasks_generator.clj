; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.server.builder.jobs.tasks-generator
  (:refer-clojure :exclude [get str keyword])
  (:require [cider-ci.utils.core :refer [keyword str presence]])
  (:require
    [cider-ci.server.builder.util :refer [json-write-str]]
    [cider-ci.utils.config :refer [get-config]]
    [cider-ci.utils.core :refer :all]
    [cider-ci.utils.http :as http]

    [clj-http.client :as http-client]
    [clojure.core.memoize :as memo]

    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug :refer [I> I>> identity-with-logging]]
    ))

(defn- file-name-to-task [file-name]
  [file-name {:environment_variables
              {:CIDER_CI_TASK_FILE file-name}}])

(defn- get-file-list_unmemoized [git-ref generate-spec]
  (let [url (str (-> (get-config) :base-url :url)
                 (when-let [context (-> (get-config) :base-url :context presence)]
                   (str "/" context))
                 "/cider-ci/repositories" "/ls-tree" "?"
                 (http-client/generate-query-string
                   (->> generate-spec
                        (merge {:git_ref git-ref
                                :include_match ""
                                :exclude_match ""
                                :submodule []
                                })
                        (map (fn [[k v]] [k (json-write-str v)]))
                        (into {}))))]
    (-> url
        (http/get {:socket-timeout 10000
                   :conn-timeout 10000
                   :as :json})
        :body)))

(def get-file-list
  (memo/lru get-file-list_unmemoized :lru/threshold 32))

; to disable caching temporarily:
;(def get-file-list get-file-list_unmemoized)

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
