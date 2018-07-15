(ns cider-ci.server.front.requests.shared
  (:refer-clojure :exclude [str keyword send-off])
(:require-macros
    [reagent.ratom :as ratom :refer [reaction]]
    [cljs.core.async.macros :refer [go]]
    )
  (:require
    [cljs.core.async :refer [timeout]]
    [cider-ci.server.front.breadcrumbs :as breadcrumbs]

    [cider-ci.utils.core :refer [str keyword deep-merge presence]]
    [reagent.core :as reagent]
    ))

(defonce state* (reagent/atom {}))

(defn response-success? [resp]
  (<= 200 (-> resp :status) 299))

(defn bootstrap-status [modal-status]
  (case modal-status
    :pending :default
    :success :success
    :error :danger))

(defn status [request]
  (cond (= nil (-> request :response)) :pending
        (-> request :response :success) :success
        :else :error))

(def fetching?*
  (reaction
    (->> @state*
         :requests
         (map (fn [[id r]] r))
         (map :response)
         (map map?)
         (map not)
         (filter identity)
         first)))

(defn dismiss-button-component
  ([request]
   (dismiss-button-component request {}))
  ([request opts]
   [:button.btn
    {:class (str "btn-" (-> request status bootstrap-status)
                 " " (:class opts))
     :on-click #(swap! state*
                       update-in [:requests]
                       (fn [rx] (dissoc rx (:id request))))}
    [:i.fas.fa-times] " Dismiss "]))
