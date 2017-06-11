;; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.server.builder.util
  (:require
    [cider-ci.utils.json-protocol]
    [cider-ci.utils.rdbms :as rdbms]

    [clojure.java.jdbc :as jdbc]
    [clojure.core.memoize :as core.memoize]

    [clj-uuid]
    [clojure.data.json :as json]
    [clojure.walk :refer [postwalk]]
    ))

(defn json-key-fn [k]
  (if (keyword? k) (subs (str k) 1) (str k) ))

(defn json-write-str [data]
  (json/write-str data :key-fn json-key-fn))

(defn id-hash [data]
  (clj-uuid/v5 clj-uuid/+null+ (json-write-str data)))

(defn idid2id [id1 id2]
  (clj-uuid/v5 clj-uuid/+null+ (str id1 id2)))

(defn stringify-keys [m]
  (let [f (fn [[k v]] [(json-key-fn k) v])]
    (postwalk (fn [x] (if (map? x) (into {} (map f x)) x)) m)))

(defn job!
  ([id] (job! id (rdbms/get-ds)))
  ([id tx] (or (->> ["SELECT * FROM jobs WHERE id = ?" id]
                    (jdbc/query tx) first)
               (throw (ex-info "Job not found" {:id id})))))

(defn task!
  ([id] (task! id (rdbms/get-ds)))
  ([id tx] (or (->> ["SELECT * FROM tasks WHERE id = ?" id]
                    (jdbc/query tx) first)
               (throw (ex-info "task not found" {:id id})))))

(def task-specification!
  (clojure.core.memoize/lu
    (fn [id & {:keys [tx]
               :or {tx (rdbms/get-ds)}} ]
      (or (->> ["SELECT data FROM task_specifications WHERE id = ?" id]
               (jdbc/query tx)
               first)
          (throw (ex-info "task-specification not found" {:id id}))))))

(defn update-state
  "Updates the state. Returns true if state
  has changed and false otherwise."

  ([tablename id state]
   (update-state tablename id state (rdbms/get-ds)))

  ([tablename id state tx]
   (->> ["id = ? AND state != ? " id state]
        (jdbc/update! tx tablename {:state state})
        first (not= 0))))


