; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.repository.sql.commits
  (:refer-clojure :exclude [str keyword find])
  (:require [cider-ci.utils.core :refer [keyword str]])
  (:require
    [cider-ci.repository.sql.commits.depth :as depth]
    [clojure.tools.logging :as logging]
    [clojure.java.jdbc :as jdbc]
    ))

(defn create! [ds params]
  (jdbc/insert! ds :commits params))

(defn update! [ds params where-clause]
  (jdbc/update! ds :commits params where-clause))

(defn find [ds id]
  (first (jdbc/query ds ["SELECT * FROM commits WHERE id = ?", id])))

(defn find! [ds id]
  (or
    (find ds id)
    (throw (IllegalStateException. (str "Could not find repository with id = " id)))))

(defn update-depths [ds]
  (depth/update-depths ds))
