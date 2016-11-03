; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.


(ns cider-ci.repository.scratch
  (:require
    [cider-ci.utils.rdbms :as rdbms]

    [clojure.data.json :as json]
    [clojure.java.jdbc :as jdbc]
    ))

