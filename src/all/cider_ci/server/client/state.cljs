(ns cider-ci.server.client.state
  (:refer-clojure :exclude [str keyword])

  (:require-macros
    [cljs.core.async.macros :as asyncm :refer (go go-loop)]
    [reagent.ratom :as ratom :refer [reaction]]
    )

  (:require
    [cider-ci.utils.core :refer [keyword str presence]]

    [cljsjs.jquery]
    [cljs.pprint :refer [pprint]]
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


;;; listen on window resize ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn windowresize-handler [event]
  (swap! client-state
         update-in [:window]
         #(merge % {:inner-width (.-innerWidth js/window)
                    :inner-height (.-innerHeight js/window)})))

(.addEventListener js/window "resize"  windowresize-handler)

(windowresize-handler nil)

;(def screen-large?*
;  (reaction (<= 1824 (-> @client-state :window :inner-width (or 768)))))

(def screen-desktop?*
  (reaction (<= 1200 (-> @client-state :window :inner-width (or 768)))))

(def screen-tablet?*
  (reaction (<= 900 (-> @client-state :window :inner-width (or 768)))))

(def screen-phone?*
  (reaction (<= 320 (-> @client-state :window :inner-width (or 768)))))


;;; init ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn init []
  (swap! server-state
         assoc
         :user (data-attribute "body" "user")
         :authentication_providers  (data-attribute
                                      "body" "authproviders")))

