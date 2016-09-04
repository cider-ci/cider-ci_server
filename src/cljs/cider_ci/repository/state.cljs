(ns cider-ci.repository.state
  (:require-macros
    [cljs.core.async.macros :as asyncm :refer (go go-loop)]
    [reagent.ratom :as ratom :refer [reaction]]
    )

  (:require
    [cider-ci.repository.constants :refer [CONTEXT]]
    [cider-ci.repository.data :refer [sort-data]]
    [cider-ci.repository.digest :refer [sha1]]

    [cljsjs.moment]
    [reagent.core :as r]
    [taoensso.sente  :as sente :refer (cb-success?)]
    [timothypratley.patchin :refer [patch]]

    ))


(defonce server-state (r/atom {}))

; this triggers the state to get out of sync which should show
; a nice alert then; TODO: test this (how?)
;(js/setTimeout #(swap! server-state assoc :x 42) 5000)

(defonce client-state
  (r/atom
    {:debug false
     :server_state_updated_at nil}))

(defn put! [k v]
  (swap! client-state (fn [cs k v]
                        (assoc cs k v))
         k v))

;(defn get [k] (clojure.core/get @client-state k))

(js/setInterval #(swap! client-state
                       (fn [s] (merge s {:timestamp (js/moment)}))) 1000)

(def db (reaction (merge @server-state @client-state)))

(defn clj->json
  [ds]
  (.stringify js/JSON (clj->js ds)))

(let [{:keys [chsk ch-recv send-fn state]}
      (sente/make-channel-socket! (str CONTEXT "/ws/chsk")
                                  {:type :auto})]
  (swap! client-state assoc :ws-connection state)
  (def chsk chsk)
  (def ch-chsk ch-recv)
  (def chsk-send! send-fn)
  (def chsk-state state))

(defn update-server-state [data]
  (when-let [full (:full data)]
    ;(js/console.log "reseting server-state")
    (reset! server-state full))
  (when-let [p (:patch data)]
    ;(js/console.log "patching server-state")
    (swap! server-state (fn [db p] (patch db p)) p))
  (swap! client-state assoc :server_state_updated_at (js/moment))
  (let [our-server-state-digest (-> @server-state sort-data prn-str sha1)
        supposed-digest (:sha1 data)]
    (if (= our-server-state-digest supposed-digest)
      (swap! client-state assoc :server-state-is-in-sync true)
      (swap! client-state assoc :server-state-is-in-sync false))))

(defn event-msg-handler [{:as message :keys [id ?data event]}]
  ;(js/console.log (clj->js {:message message}))
  (when (= id :chsk/recv)
    (let [[event-id data] ?data]
      (when (and event-id data)
        ;(js/console.log (clj->js {:event-id event-id :data data}))
        (case event-id
          :cider-ci.repository/db (update-server-state data))
        ))))


(sente/start-chsk-router! ch-chsk event-msg-handler)

;(js* "debugger;")
