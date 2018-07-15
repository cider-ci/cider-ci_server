; Copyright © 2013 - 2017 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.server.socket
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer [keyword str]])
  (:require
    [cider-ci.server.socket.back :as socket.back]
    ;[cider-ci.server.socket.push-db :as push-db]
    [cider-ci.server.socket.push-table-events :as push-table-events]
    [cider-ci.server.paths :refer [path]]
    [cider-ci.server.state :as server.state]
    [cider-ci.server.repository.web.edn]

    [cider-ci.auth.anti-forgery :as anti-forgery]
    [cider-ci.auth.authorize :as authorize]
    [cider-ci.utils.ring]
    [cider-ci.utils.routing :as routing]

    [compojure.core :as cpj]
    [ring.middleware.defaults :refer [site-defaults wrap-defaults]]
    [taoensso.sente :as sente]

    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.debug :as debug :refer [I> I>> identity-with-logging]]
    [logbug.ring :refer [wrap-handler-with-logging]]
    [logbug.thrown :as thrown]
    ))



(defn sort-data [data]
  (cond
    (map? data)  (->> data
                      (map (fn [[k v]] [k (sort-data v)]))
                      (sort-by (fn [[k _]] k))
                      (into {}))
    :else data))



;##############################################################################


(def routes
  (cpj/routes
    (cpj/GET  (path :websockets) [] #'socket.back/ring-ajax-get-or-ws-handshake)
    (cpj/POST (path :websockets) [] #'socket.back/ring-ajax-post)))


;##############################################################################

(defn initialize []
  (socket.back/initialize)
  ;(push-db/initialize)
  (push-table-events/initialize))

;#### debug ###################################################################
;(logging-config/set-logger! :level :debug)
;(debug/wrap-with-log-debug #'target-remote-state)
;(debug/wrap-with-log-debug #'push-data2)
;(debug/wrap-with-log-debug #'push-to-client)
;(debug/wrap-with-log-debug #'push-to-client-swap-fn)
;(debug/wrap-with-log-debug #'db-state-filter-repositories)
;(debug/wrap-with-log-debug #'db-state-set-user)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns 'cider-ci.auth.authorize)
(debug/debug-ns *ns*)
