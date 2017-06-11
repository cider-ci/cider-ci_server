(ns cider-ci.server.client.state
  (:refer-clojure :exclude [str keyword])

  (:require-macros
    [cljs.core.async.macros :as asyncm :refer (go go-loop)]
    [reagent.ratom :as ratom :refer [reaction]]
    )

  (:require
    [cider-ci.utils.core :refer [keyword str presence]]

    [cljsjs.moment]
    [goog.dom :as dom]
    [goog.dom.dataset :as dataset]
    [reagent.core :as reagent]
    ))


(defonce debug-db (reagent/atom {}))

;;; server-state  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defonce server-state (reagent/atom {}))

(defn data-attribute [element-name attribute-name]
  (-> (.getElementsByTagName js/document element-name)
      (aget 0)
      (dataset/get attribute-name)
      (#(.parse js/JSON %))
      cljs.core/js->clj
      clojure.walk/keywordize-keys))


;;; page-state ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defonce page-state
  (reagent/atom {}))


;;; client-state  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defonce client-state
  (reagent/atom {:debug false
                 :commits-page {:form-data {}}
                 }))

(js/setInterval #(swap! client-state
                       (fn [s] (merge s {:timestamp (js/moment)}))) 1000)

(defn clj->json
  [ds]
  (.stringify js/JSON (clj->js ds)))


;;; init ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn init []
  (swap! server-state
         assoc
         :user (data-attribute "body" "user")
         :authentication_providers  (data-attribute
                                      "body" "authproviders")))

