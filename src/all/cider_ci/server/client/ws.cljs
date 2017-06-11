(ns cider-ci.server.client.ws
  (:refer-clojure :exclude [str keyword])
  (:require-macros
    [cljs.core.async.macros :as asyncm :refer (go go-loop)]
    [reagent.ratom :as ratom :refer [reaction]]
    )
  (:require
    [cider-ci.utils.core :refer [keyword str presence]]
    [cider-ci.server.client.state :refer [client-state server-state debug-db]]
    [cider-ci.utils.digest :refer [digest]]

    [taoensso.sente  :as sente :refer (cb-success?)]
    [timothypratley.patchin :refer [diff patch]]
    [reagent.core :as reagent]
    ))

(defonce push-pending? (atom false))

(def client-state-push-to-server-keys [:current-page])

(add-watch
  client-state :set-push-pending
  (fn [_ _ old-state new-state]
    (when (not= (select-keys old-state client-state-push-to-server-keys)
                (select-keys new-state client-state-push-to-server-keys))
      (reset! push-pending? true))))

(declare chsk ch-chsk chsk-send! chsk-state)

(defn update-server-state [data]
  (let [new-state (if-let [full (:full data)]
                    (reset! server-state full)
                    (if-let [p (:patch data)]
                      (swap! server-state (fn [db p] (patch db p)) p)
                      (throw (ex-info "Either :full or :patch must be supplied" {}))))
        _ (swap! client-state assoc :server_state_updated_at (js/moment))
        new-state-digest (digest new-state)
        supposed-digest (:digest data)]
    (if (= new-state-digest supposed-digest)
      (swap! client-state assoc :server-state-is-in-sync true)
      (do (swap! client-state assoc :server-state-is-in-sync false)
          (swap! debug-db  assoc
                 :last-bad-server-state-sync-at (js/moment)
                 :last-bad-server-state-sync-data new-state)))))

(defn event-msg-handler [{:as message :keys [id ?data event]}]
    ;(js/console.log (clj->js {:id id :message message}))
    (case id
      :chsk/recv (let [[event-id data] ?data]
                   (when (and event-id data)
                     ;(js/console.log (clj->js {:event-id event-id :data data}))
                     (case event-id
                       :cider-ci.server.repository/db (update-server-state data))
                     ))
      :chsk/state (let [[_ new-state] ?data]
                    (swap! client-state assoc :connection
                           (assoc new-state :updated_at (js/moment))
                           ))
      nil))

(defn push-to-server []
  (if-not @push-pending?
    (js/setTimeout push-to-server 200)
    (do (reset! push-pending? false)
        (chsk-send! [:client/state
                     {:full (select-keys @client-state
                                         client-state-push-to-server-keys)}]
                    1000
                    (fn [reply]
                      (when-not (sente/cb-success? reply)
                        (reset! push-pending? true))
                      (js/setTimeout push-to-server 200))))))

(defn init []
  (let [{:keys [chsk ch-recv send-fn state]}
        (sente/make-channel-socket! "/cider-ci/server/ws/chsk"
                                    {:type :auto})]
    (swap! client-state assoc :ws-connection state)
    (def chsk chsk)
    (def ch-chsk ch-recv)
    (def chsk-send! send-fn)
    (def chsk-state state))
  (sente/start-chsk-router! ch-chsk event-msg-handler)
  (js/setTimeout push-to-server 100))


