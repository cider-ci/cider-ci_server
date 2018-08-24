(ns cider-ci.server.socket.front
  (:refer-clojure :exclude [str keyword])
  (:require-macros
    [cljs.core.async.macros :as asyncm :refer (go go-loop)]
    [reagent.ratom :as ratom :refer [reaction]]
    )
  (:require
    [cider-ci.server.front.state :as state]
    [cljs.core.async :as async :refer [<!]]
    [cider-ci.server.client.shared :refer [pre-component-pprint]]
    [cider-ci.server.client.state :refer [client-state server-state debug-db page-state]]
    [cider-ci.server.paths :refer [path]]
    [cider-ci.utils.core :refer [keyword str presence]]
    [cider-ci.utils.digest :refer [digest]]

    [cljs.pprint :refer [pprint]]
    [reagent.core :as reagent]
    [taoensso.sente  :as sente :refer (cb-success?)]
    [timothypratley.patchin :refer [diff patch]]
    ))


(declare chsk ch-chsk chsk-send! chsk-state)

(defn dispatch-server-entity-event [event]
  (let [routing-state @state/routing-state*
        handler (-> routing-state :event-handler :handler)
        table-names (-> routing-state :event-handler :table-names)]
    (when handler
      (handler event))))

(defn event-msg-handler [{:as message :keys [id ?data event]}]
  ;(js/console.log (with-out-str (pprint {:id id :message message})))
  (case id
    :chsk/recv (let [[event-id data] ?data]
                 (swap! state/socket* assoc :msg_received_at (js/moment))
                 (when (and event-id data)
                   ;(js/console.log (clj->js {:event-id event-id :data data}))
                   (case event-id
                     :cider-ci/entity-event (dispatch-server-entity-event data))))
    :chsk/state (let [[_ new-state] ?data]
                  (swap! state/socket* assoc :connection
                         (assoc new-state :updated_at (js/moment))))
    nil))


;;; routing state ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defonce push-routing-state-pending-chan (async/chan (async/dropping-buffer 1)))

(defn set-push-routing-state-pending []
  (go (async/>! push-routing-state-pending-chan 1))) 

(defn push-routing-state-to-server! []
  (if (-> @state/socket* :connection :open?)
    (chsk-send! [:front/routing-state (dissoc @state/routing-state* :page)]
                1000
                (fn [reply]
                  ;(js/console.log (with-out-str (pprint ["push-routing-state-to-server!/reply" reply])))
                  (when-not (sente/cb-success? reply)
                    (set-push-routing-state-pending))))
    (set-push-routing-state-pending)))

(defn start-push-routing-state-to-server []
  (go-loop []
           (let [_ (<! push-routing-state-pending-chan)]
             (push-routing-state-to-server!)
             (recur))))

(add-watch
  state/routing-state* :set-push-pending
  (fn [_ _ _ _]
    (set-push-routing-state-pending)))


;;; init ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn init []
  (let [{:keys [chsk ch-recv send-fn state]}
        (sente/make-channel-socket! (path :websockets)
                                    {:type :auto})]
    (swap! state/socket* assoc :connection state)
    (def chsk chsk)
    (def ch-chsk ch-recv)
    (def chsk-send! send-fn)
    (def chsk-state state))
  (sente/start-chsk-router! ch-chsk event-msg-handler)
  ; doesn't work here because socket is not open yet
  (start-push-routing-state-to-server)
  )


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
  [:div.page.socket
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
     [:section.state-socket
      [:h3 "@state/socket*"]
      [pre-component-pprint @state/socket*]]]]
   ])

