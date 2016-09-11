(ns cider-ci.repository.web.push
  (:require
    [cider-ci.repository.state :as state]
    [cider-ci.repository.web.shared :as web.shared]
    [cider-ci.repository.web.edn]
    [cider-ci.repository.data :refer [sort-data]]

    [cider-ci.auth.anti-forgery :as anti-forgery]
    [cider-ci.auth.authorize :as authorize]
    [cider-ci.utils.digest :refer [digest]]

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

(def clients (atom {}))

;(-> @clients first second keys digest)
;(->> @clients (map #(let [[k _] %] k)))

;##############################################################################

;##############################################################################

(defn user-id [req]
  (logging/debug 'user-id req)
  (str (-> req :authenticated-user :id) "_" (:client-id req)))

(defn anti-forgery-token [req]
  (logging/debug 'anti-forgery-token req)
  (let [token (-> req :cider-ci_anti-forgery-token)]
    (logging/debug 'token token)
    token))

(let [{:keys [ch-recv send-fn connected-uids
              ajax-post-fn ajax-get-or-ws-handshake-fn]}
      (sente/make-channel-socket!
        (get-sch-adapter) {:user-id-fn user-id
                           :csrf-token-fn anti-forgery-token
                           })]
  (def ring-ajax-post                (anti-forgery/wrap ajax-post-fn)) ;TODO enable this
  (def ring-ajax-get-or-ws-handshake ajax-get-or-ws-handshake-fn)
  (def ch-chsk                       ch-recv) ; ChannelSocket's receive channel
  (def chsk-send!                    send-fn) ; ChannelSocket's send API fn
  (def connected-uids                connected-uids) ; Watchable, read-only atom
  )

;##############################################################################

(defn filter-repositories [db user]
  (assoc db :repositories
         (I>> identity-with-logging
              (:repositories db)
              (map #(let [[_ v] %] v))
              (map #(dissoc % :proxy_id))
              (map #(web.shared/filter-repository-params  % user))
              (map (fn [r] [(:id r) r]))
              (into {}))))

(defn push-data [db user-id]
  (let [user (get (:users db) user-id {})]
    (-> db
        (dissoc :users)
        (assoc :user (dissoc user :password_digest))
        (filter-repositories user)
        json/write-str
        (json/read-str :key-fn keyword))))

(defn push-to-client [db client-id]
  (let [user-id (-> client-id (clojure.string/split #"_") first)
        target-remote-state (push-data db user-id)
        target-remote-state-digest (digest target-remote-state)
        push-data (atom nil)
        swap-fn (fn [clients]
                  (if-not (contains?  clients client-id)
                    clients ; nothing known about this client-id
                    (do (if-let [current-remote-state (get clients client-id nil)]
                          (let [d (diff current-remote-state target-remote-state)]
                            (reset! push-data {:patch d
                                               :digest target-remote-state-digest}))
                          (reset! push-data {:full target-remote-state
                                             :digest target-remote-state-digest}))
                        (assoc clients client-id target-remote-state))))]
    (def ^:dynamic *last-pushed-data* {:data target-remote-state :digest target-remote-state-digest})
    (logging/debug 'target-remote-state (prn-str target-remote-state))
    (swap! clients swap-fn)
    (logging/debug 'pushing [client-id @push-data])
    (chsk-send! client-id [(keyword "cider-ci.repository" "db")
                           @push-data])))

(defn push-to-all-clients
  ([] (push-to-all-clients @state/db))
  ([db] (doseq [[client-id _] @clients]
          (push-to-client db client-id))))

(add-watch state/db :set-update-to-all-clients
           (fn [_ _ old_state new_state]
             (when (not= (-> old_state prn-str)
                         (-> new_state prn-str))
               (push-to-all-clients new_state))))

;##############################################################################

(defn update-connected-clients-list [_ _ old-state new-state]
  (let [current-clients (-> new-state :any)
        removed-clients (difference (-> @clients keys set) current-clients)
        added-clients (difference current-clients (-> @clients keys set))]
    (logging/debug {:current-clients current-clients
                    :removed-clients removed-clients
                    :added-clients added-clients})
    (doseq [removed-client removed-clients]
      (swap! clients (fn [cs cid] (dissoc cs cid)) removed-client))
    (doseq [added-client added-clients]
      (swap! clients (fn [cs cid] (assoc cs cid nil)) added-client)
      (push-to-client @state/db added-client))))

(add-watch connected-uids :connected-uids #'update-connected-clients-list)



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
                                    (authorize/wrap-require! {:user true})))
             (cpj/ANY "*" _ handler))[req])))

;#### debug ###################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns 'cider-ci.auth.http-basic)
;(debug/debug-ns *ns*)
