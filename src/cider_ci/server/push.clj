; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.server.push
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer [keyword str]])

  (:require
    [cider-ci.server.state :as server.state]
    [cider-ci.repository.web.edn]

    [cider-ci.auth.anti-forgery :as anti-forgery]
    [cider-ci.auth.authorize :as authorize]
    [cider-ci.utils.digest :refer [digest]]
    [cider-ci.utils.ring]

    [fipp.edn :refer (pprint) :rename {pprint fipp}]
    [clojure.data.json :as json]
    [clojure.set :refer [difference]]
    [compojure.core :as cpj]
    [ring.middleware.defaults :refer [site-defaults wrap-defaults]]
    [taoensso.sente :as sente]
    [taoensso.sente.server-adapters.aleph :refer [get-sch-adapter]]
    [timothypratley.patchin :refer [diff]]

    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.debug :as debug :refer [I> I>> identity-with-logging]]
    [logbug.thrown :as thrown]
    ))

(defonce user-clients (atom {}))

(defn sort-data [data]
  (cond
    (map? data)  (->> data
                      (map (fn [[k v]] [k (sort-data v)]))
                      (sort-by (fn [[k _]] k))
                      (into {}))
    :else data))


;##############################################################################

(defn user-client-id [req]
  (logging/debug 'user-client-id req)
  (str (-> req :authenticated-entity :id) "_" (:client-id req)))

(defn anti-forgery-token [req]
  (logging/debug 'anti-forgery-token req)
  (let [token (-> req :cider-ci_anti-forgery-token)]
    (logging/debug 'token token)
    token))

(declare chsk-send!
         connected-user-clients
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
    (def ring-ajax-post                (anti-forgery/wrap ajax-post-fn))
    (def ring-ajax-get-or-ws-handshake ajax-get-or-ws-handshake-fn)
    (def ch-chsk ch-recv)
    (def chsk-send! send-fn)
    (def connected-user-clients connected-uids))
  (sente/start-server-chsk-router! ch-chsk #'event-msg-handler))


;### receive data #############################################################

(defn event-msg-handler [{mkey :id data :?data client-id :uid}]
  ;(logging/info 'MESSAGE-RECEIVED [mkey client-id data])
  (case mkey
    :client/state (swap! user-clients assoc-in
                         [client-id :client-state] (:full data))
    :chsk/uidport-open nil
    :chsk/uidport-close nil
    ))


;### push data ################################################################

(defn push-data [db user-client-id]
  (let [user (get (:users db) user-client-id {})]
    (-> db
        (dissoc :users)
        (assoc :user (dissoc user :password_digest))
        json/write-str
        (json/read-str :key-fn keyword))))

(defn push-to-client [db user-client-id]
  (let [user-id (-> user-client-id (clojure.string/split #"_") first)
        target-remote-state (push-data db user-id)
        target-remote-state-digest (digest target-remote-state)
        push-data (atom nil)
        swap-fn (fn [user-clients]
                  (if-not (contains? user-clients user-client-id)
                    user-clients ; nothing known about this user-client-id
                    (do (if-let [current-remote-state (:server-state (get user-clients user-client-id nil))]
                          (let [d (diff current-remote-state target-remote-state)]
                            (reset! push-data {:patch d
                                               :digest target-remote-state-digest}))
                          (reset! push-data {:full target-remote-state
                                             :digest target-remote-state-digest}))
                        (assoc-in user-clients [user-client-id :server-state] target-remote-state))))]
    (def ^:dynamic *last-pushed-data* {:data target-remote-state :digest target-remote-state-digest})
    (logging/debug 'target-remote-state (prn-str target-remote-state))
    (swap! user-clients swap-fn)
    (logging/debug 'pushing [user-client-id @push-data])
    (chsk-send! user-client-id [(keyword "cider-ci.repository" "db")
                           @push-data])))

(defn push-to-all-user-clients
  ([db] (doseq [[user-client-id _] @user-clients]
          (push-to-client db user-client-id))))

(defn initialize-watch-state-db []
  (server.state/watch-db
    :send-update-to-all-user-clients
    (fn [_ _ old_state new_state]
      (when (not= (-> old_state prn-str)
                  (-> new_state prn-str))
        (push-to-all-user-clients new_state)))))


;##############################################################################


(defn update-connected-user-clients-list [_ _ old-state new-state]
  (let [current-user-clients (-> new-state :any)
        removed-user-clients (difference (-> @user-clients keys set) current-user-clients)
        added-user-clients (difference current-user-clients (-> @user-clients keys set))]
    (logging/debug {:current-user-clients current-user-clients
                    :removed-user-clients removed-user-clients
                    :added-user-clients added-user-clients})
    (doseq [removed-client removed-user-clients]
      (swap! user-clients (fn [cs cid] (dissoc cs cid)) removed-client))
    (doseq [added-client added-user-clients]
      (swap! user-clients (fn [cs cid] (assoc cs cid nil)) added-client)
      (push-to-client (server.state/get-db) added-client))))

(defn update-connected-user-clients-list-when-connected-uis-change []
  (add-watch connected-user-clients :connected-user-clients #'update-connected-user-clients-list))

;##############################################################################


(cpj/defroutes routes
  (cpj/GET  "/ws/chsk" req (ring-ajax-get-or-ws-handshake req))
  (cpj/POST "/ws/chsk" req (ring-ajax-post                req)))

(defn wrap [handler]
  (fn [req]
    (logging/debug 'web.push/handler req)
    (apply (cpj/routes
             (cpj/ANY "/ws/chsk" _ (I> identity-with-logging
                                       #'routes
                                       (wrap-defaults site-defaults)
                                       (authorize/wrap-require! {:user true})
                                       ))
             (cpj/ANY "*" _ handler))[req])))

;##############################################################################


(defn initialize []
  (initialize-sente)
  (update-connected-user-clients-list-when-connected-uis-change)
  (initialize-watch-state-db)
  ;(debug/debug-ns *ns*)
  )

;#### debug ###################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns 'cider-ci.auth.authorize)
;(debug/debug-ns *ns*)
