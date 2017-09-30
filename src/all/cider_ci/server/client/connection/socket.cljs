(ns cider-ci.server.client.connection.socket
  (:refer-clojure :exclude [str keyword])
  (:require-macros
    [cljs.core.async.macros :as asyncm :refer (go go-loop)]
    [reagent.ratom :as ratom :refer [reaction]]
    )
  (:require
    [cider-ci.utils.core :refer [keyword str presence]]
    [cider-ci.server.client.state :refer [client-state server-state debug-db page-state]]
    [cider-ci.server.client.connection.state :as state]
    [cider-ci.utils.digest :refer [digest]]
    [cider-ci.server.client.shared :refer [pre-component-pprint]]

    [cljs.pprint :refer [pprint]]
    [reagent.core :as reagent]
    [taoensso.sente  :as sente :refer (cb-success?)]
    [timothypratley.patchin :refer [diff patch]]
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
        _ (swap! state/socket* assoc :server_state_updated_at (js/moment))
        new-state-digest (digest new-state)
        supposed-digest (:digest data)]
    (if (= new-state-digest supposed-digest)
      (swap! state/socket* assoc :server-state-is-in-sync true)
      (do (swap! state/socket* assoc :server-state-is-in-sync false)
          (swap! debug-db  assoc
                 :last-bad-server-state-sync-at (js/moment)
                 :last-bad-server-state-sync-data new-state)))))

(defn dispatch-server-entity-event [event]
  (when-let [page-receiver (:server-entity-event-receiver @page-state)]
    (page-receiver event)))

(defn event-msg-handler [{:as message :keys [id ?data event]}]
  ;(js/console.log (with-out-str (pprint {:id id :message message})))
    (case id
      :chsk/recv (let [[event-id data] ?data]
                   (swap! state/socket* assoc :msg_received_at (js/moment))
                   (when (and event-id data)
                     ;(js/console.log (clj->js {:event-id event-id :data data}))
                     (case event-id
                       :cider-ci/state-db (update-server-state data)
                       :cider-ci/entity-event (dispatch-server-entity-event data))))
      :chsk/state (let [[_ new-state] ?data]
                    (swap! state/socket* assoc :connection
                           (assoc new-state :updated_at (js/moment))))
      nil))

(defn push-to-server []
  (if-not @push-pending?
    (js/setTimeout push-to-server 200)
    (do (reset! push-pending? false)
        (swap! state/socket* assoc :msg_sent_at (js/moment))
        (chsk-send! [:client/state
                     {:full (select-keys @client-state
                                         client-state-push-to-server-keys)}]
                    1000
                    (fn [reply]
                      ;(js/console.log (with-out-str (pprint ["push-to-server/reply" reply])))
                      (when-not (sente/cb-success? reply)
                        (reset! push-pending? true))
                      (js/setTimeout push-to-server 200))))))

(defn init []

  (let [{:keys [chsk ch-recv send-fn state]}
        (sente/make-channel-socket! "/cider-ci/server/ws/chsk"
                                    {:type :auto})]
    (swap! state/socket* assoc :ws-connection state)
    (def chsk chsk)
    (def ch-chsk ch-recv)
    (def chsk-send! send-fn)
    (def chsk-state state))
  (sente/start-chsk-router! ch-chsk event-msg-handler)
  (js/setTimeout push-to-server 100))


;;; icon and bg-state ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn icon-component []
  (if @state/socket-active?*
    [:i.fa.fa-spinner.fa-pulse]
    [:i.fa.fa-spinner]))

(def socket-bg-color-class*
  (reaction
    (if-let [conn (-> @state/socket* :connection)]
      (cond
        (-> conn :ever-opened? not) "warning-bg"
        (-> conn :open?) "success-bg"
        :else "error-bg")
      "warning-bg")))


;;; ui ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn page []
  [:div.page.requests
   [:div.title
    [:h1 "Socket"]
    [:p "This page shows the status of the WebSocket connection. "
     "It is useful for debugging connection problems."
     ]
    [:section.debug
     [:hr]
     [:section.jquery
      [:h3 "jquery"]
      [pre-component-pprint (.resize (js/$ "window"))]]
     [:section.active
      [:h3 "@state/socket-active?*"]
      [pre-component-pprint @state/socket-active?*]]
     [:section.push-pending
      [:h3 "@push-pending?"]
      [pre-component-pprint @push-pending?]]
     [:section.state-socket
      [:h3 "@state/socket*"]
      [pre-component-pprint @state/socket*]]]]
   ])

