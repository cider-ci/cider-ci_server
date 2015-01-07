; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.repository.sql.commits
  (:refer-clojure :exclude [find])
  (:require 
    [clojure.tools.logging :as logging]
    [clojure.java.jdbc :as jdbc]
    ))

(defn create! [ds params]
  (jdbc/insert! ds :commits params))

(defn update! [ds params where-clause]
  (jdbc/update! ds :commits params where-clause))

(defn find [ds id]
  (first (jdbc/query ds ["SELECT * FROM commits WHERE id = ?", id])))
  ; (find "6712b320e6998988f023ea2a6265e2d781f6e959")

(defn find! [ds id]
  (or 
    (find ds id) 
    (throw (IllegalStateException. (str "Could not find repository with id = " id)))))
  ;(find! "x")
  ;(find! "416a312495a4eac45bd7629fa7df1dfb01a1117b" )

(defn update-depths [ds]
  (jdbc/execute! ds
    ["UPDATE commits
      SET depth = depths.depth
      FROM depths
      WHERE commits.id = depths.commit_id"]))

