(ns cider-ci.utils.state
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer [keyword str deep-merge]])
  (:require
    [clojure.set :refer [difference]]

    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.debug :as debug :refer [I> I>> identity-with-logging]]
    [logbug.ring :refer [wrap-handler-with-logging]]
    [logbug.thrown :as thrown]
    ))


(defn to-be-removed-keys [current target]
  (difference
    (-> current keys set)
    (-> target keys set)))

(defn delete-removed [current target]
  (apply dissoc current (to-be-removed-keys current target)))

(def update-existing deep-merge)

(defn update-rows
  "In the first form [current target] updates a current map to the desired
  target map where each is assumed to be a map of ids (keys) to rows (values).
  Each row will always be amended, i.e. non existing values in target but
  existing in current will be kept. Rows for ids not existing in the target but
  existing in current will be completely removed.
  In the second form [current target sub-keys] the to be updated current is
  located under the sub-keys seq."
  ([current target]
   (-> current
       (delete-removed target)
       (update-existing target)))
  ([current target sub-keys]
   (if (empty? sub-keys)
     (update-rows current target)
     (update-in current sub-keys
                (fn [current]
                  (update-rows current target))))))

;#### debug ###################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns 'cider-ci.utils.row-events)
;(debug/debug-ns *ns*)
