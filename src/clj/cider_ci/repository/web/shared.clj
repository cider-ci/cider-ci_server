; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.repository.web.shared
  (:require

    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.debug :as debug :refer [I> I>>]]
    [logbug.thrown :as thrown]
    ))

(defn respond-with-500 [request ex]
  (logging/warn "RESPONDING WITH 500" {:exception (thrown/stringify ex) :request request})
  {:status 500 :body (thrown/stringify ex)})


