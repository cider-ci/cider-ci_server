; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.repository.sql.branches
  (:require
    [clojure.tools.logging :as logging]
    [clojure.java.jdbc :as jdbc]
    ))

(defn create! [ds params]
  (logging/debug create! [params])
  (jdbc/insert! ds :branches params))

(defn for-repository [ds canonic-id]
  (jdbc/query ds
    ["SELECT * FROM branches WHERE repository_id = ? " canonic-id]))



