(ns cider-ci.server.front.requests.core
  (:refer-clojure :exclude [str keyword send-off])
  (:require-macros
    [reagent.ratom :as ratom :refer [reaction]]
    [cljs.core.async.macros :refer [go]]
    )
  (:require
    [cider-ci.server.anti-csrf.core :as anti-csrf]
    [cider-ci.server.front.requests.modal]
    [cider-ci.server.front.requests.shared :as shared]
    [cider-ci.server.front.state :as state]

    [cider-ci.env]
    [cider-ci.utils.core :refer [str keyword deep-merge presence]]

    [cljs-http.client :as http-client]
    [cljs-uuid-utils.core :as uuid]
    [cljs.core.async :as async]
    [cljs.core.async :refer [timeout]]
    [cljsjs.moment]
    [clojure.pprint :refer [pprint]]
    [goog.string :as gstring]
    [goog.string.format]
    [reagent.core :as reagent]))


;;; request per se ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def META-DEFAULTS {:autoremove-on-success true
                    :autoremove-delay 1000})

(defn autoremove [id meta]
  (go (<! (timeout (:autoremove-delay meta)))
      (swap! shared/state* assoc-in
             [:requests id :meta :modal] false)
      (<! (timeout 30000))
      (swap! shared/state* update :requests
             (fn [rqs] (dissoc rqs id)))))

(defn update-progress [id progress-chan]
  (go (let [progress (<! progress-chan)]
        ;(js/console.log (clj->js {:progress progress}))
        (swap! shared/state*
               (fn [state id progress]
                 (if-not (-> state :requests (get id))
                   state
                   (assoc-in state [:requests id :progress] progress)))
               id progress))))

(defn request [id req meta chan callback]
  (go (<! (timeout cider-ci.env/request-delay))
      (let [resp (<! (http-client/request req))]
        (when (-> @shared/state* :requests (get id))
          (swap! shared/state*
                 update-in [:requests id]
                 (fn [req resp]
                   (merge req {:response resp
                               :responsed_at (js/moment)}))
                 resp)
          (when (and (shared/response-success? resp)
                     (:autoremove-on-success meta))
            (autoremove id meta)))
        (when callback (callback resp))
        (when chan (>! chan resp)))))

(defn send-off [req-opts meta-opts & {:keys [callback chan]
                                      :or {callback nil chan nil}}]
  (let [id (uuid/uuid-string (uuid/make-random-uuid))
        progress-chan (async/chan)
        req (deep-merge {:method :post
                         :headers {"accept" "application/json"
                                   "X-CSRF-Token" (anti-csrf/anti-csrf-token)}
                         :progress progress-chan}
                        req-opts)
        meta (deep-merge META-DEFAULTS
                         {:modal (case (:method req)
                                   (:get :head) false
                                   true)}
                         meta-opts)]
    (swap! shared/state* assoc-in [:requests id]
           {:request req
            :meta meta
            :id id
            :key id
            :requested_at (js/moment)
            })
    (update-progress id progress-chan)
    (request id req meta chan callback)
    id))

(def query-string http-client/generate-query-string)


;;; icon ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn icon-component []
  (if @shared/fetching?*
    [:i.fas.fa-sync-alt.fa-spin]
    [:i.fas.fa-sync-alt]))





