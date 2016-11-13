; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.repository.web.shared
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer [keyword str]])
  (:require
    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.debug :as debug :refer [I> I>>]]
    [logbug.thrown :as thrown]
    ))

;##############################################################################

(def to-be-removed-properties
  [[:proxy_id]])

(defn dissoc-in [m ks]
  (if (empty? (rest ks))
    (dissoc m (last ks))
    (update-in m (-> ks reverse rest reverse) dissoc (last ks))))

;(dissoc-in {:a 41 :b 5} [:a])
;(dissoc-in {:a {:b {:c 7 :y 42}}} [:a :b :c])

(defn remove-properties [repository]
  (loop [repository repository
         to-be-removed-properties to-be-removed-properties]
    (if (empty? to-be-removed-properties)
      repository
      (recur (dissoc-in repository (first to-be-removed-properties))
             (rest to-be-removed-properties)))))

;##############################################################################

(def to-be-hidden-non-admin-properties
  [[:update_notification_token]
   [:fetch-and-update :last_error]
   [:remote_api_token]
   [:remote_api_token_bearer]
   [:push-hook :hook]
   [:push-hook :last_error]
   [:status-pushes :last_error]
   [:branch-updates :last_error]])

(defn hide-non-admin-properties [repository]
  (loop [repository repository
         to-be-hidden-non-admin-properties to-be-hidden-non-admin-properties]
    (if (empty? to-be-hidden-non-admin-properties)
      repository
      (recur (update-in repository (first to-be-hidden-non-admin-properties)
                        (fn [old] (if (nil? old)
                                    nil
                                    "********")))
             (rest to-be-hidden-non-admin-properties)))))

;(hide-non-admin-properties {:update_notification_token "secret"})

;##############################################################################


(defn filter-repository-params [repository user]
  (as-> repository repository
    (remove-properties repository)
    (if (:is_admin user)
      repository
      (hide-non-admin-properties repository))))

(defn respond-with-500 [request ex]
  (logging/warn "RESPONDING WITH 500" {:exception (thrown/stringify ex) :request request})
  {:status 500 :body (thrown/stringify ex)})


