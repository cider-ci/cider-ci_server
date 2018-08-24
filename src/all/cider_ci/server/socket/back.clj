; Copyright © 2013 - 2018 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.server.socket.back
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer [keyword str]])

  (:require
    [cider-ci.server.repository.web.edn]
    [cider-ci.server.socket.shared :refer [user-clients*]]
    ;[cider-ci.server.socket.push-db :as push-db]
    [cider-ci.constants :as constants]

    [cider-ci.auth.anti-forgery :as anti-forgery]
    [cider-ci.auth.authorize :as authorize]
    [cider-ci.utils.daemon :refer [defdaemon]]
    [cider-ci.utils.ring]
    [cider-ci.utils.routing :as routing]

    [clojure.set :refer [difference]]
    [fipp.edn :refer (pprint) :rename {pprint fipp}]
    [ring.middleware.defaults :refer [site-defaults wrap-defaults]]
    [taoensso.sente :as sente]
    [taoensso.sente.server-adapters.aleph :refer [get-sch-adapter]]

    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.debug :as debug :refer [I> I>> identity-with-logging]]
    [logbug.ring :refer [wrap-handler-with-logging]]
    [logbug.thrown :as thrown]
    ))



;##############################################################################

(defn user-client-id [req]
  ;(logging/debug 'user-client-id req)
  (str (-> req :authenticated-entity :id) "_" (:client-id req)))

(defn anti-forgery-token [req]
  (def ^:dynamic *req* req)
  (some-> req
          (get :cookies)
          (get constants/ANTI_CRSF_TOKEN_COOKIE_NAME)))

(declare connected-user-clients
         ring-ajax-post
         ring-ajax-get-or-ws-handshake
         event-msg-handler)

(defn initialize-sente []
  (let [{:keys [ch-recv send-fn connected-uids
                ajax-post-fn ajax-get-or-ws-handshake-fn]}
        (sente/make-channel-socket!
          (get-sch-adapter) {:user-id-fn user-client-id
                             :csrf-token-fn anti-forgery-token
                             })]
    (def ring-ajax-post (anti-forgery/wrap ajax-post-fn))
    (def ring-ajax-get-or-ws-handshake ajax-get-or-ws-handshake-fn)
    (def ch-chsk ch-recv)
    (cider-ci.server.socket.shared/initialize-chsk-send! send-fn)
    (def connected-user-clients connected-uids))
  (sente/start-server-chsk-router! ch-chsk #'event-msg-handler))


;### receive data #############################################################

(defn event-msg-handler [{mkey :id
                          data :?data
                          client-id :uid
                          ?reply-fn :?reply-fn}]
  ;(logging/info 'MESSAGE-RECEIVED [mkey client-id data])
  (case mkey
    :client/state (do (swap! user-clients* assoc-in
                             [client-id :client-state] (:full data))
                      ;(future (push-db/push-to-client client-id))
                      )
    :front/routing-state (swap! user-clients* assoc-in 
                                [client-id :routing-state] data)
    :chsk/uidport-open nil
    :chsk/uidport-close nil
    :chsk/ws-ping nil)
  (when (fn? ?reply-fn) (?reply-fn true)))


;##############################################################################

(defn update-connected-user-clients-list [_ _ old-state new-state]
  (let [current-user-clients (-> new-state :any)
        removed-user-clients (difference (-> @user-clients* keys set) current-user-clients)
        added-user-clients (difference current-user-clients (-> @user-clients* keys set))]
    ;(logging/debug {:current-user-clients current-user-clients :removed-user-clients removed-user-clients :added-user-clients added-user-clients})
    (doseq [removed-client removed-user-clients]
      (swap! user-clients* (fn [cs cid] (dissoc cs cid)) removed-client))
    (doseq [added-client added-user-clients]
      (swap! user-clients* (fn [cs cid] (assoc cs cid nil)) added-client)
      ;(future (push-db/push-to-client added-client))
      )))

(defn update-connected-user-clients-list-when-connected-uis-change []
  (add-watch connected-user-clients :connected-user-clients #'update-connected-user-clients-list))


;##############################################################################

(defn initialize []
  (initialize-sente)
  (update-connected-user-clients-list-when-connected-uis-change))

;#### debug ###################################################################
;(logging-config/set-logger! :level :debug)
;(debug/debug-ns *ns*)
