(ns cider-ci.ui2.ui.state
  (:refer-clojure :exclude [str keyword])

  (:require-macros
    [cljs.core.async.macros :as asyncm :refer (go go-loop)]
    [reagent.ratom :as ratom :refer [reaction]]
    )

  (:require
    [cider-ci.utils.core :refer [keyword str presence]]
    [cider-ci.utils.digest :refer [digest]]
    [cljsjs.moment]
    [reagent.core :as r]
    [timothypratley.patchin :refer [patch]]
    ))


(defonce debug-db (r/atom {}))

(defonce server-state (r/atom {}))

; this triggers the state to get out of sync which should show
; a nice alert then; TODO: test this (how?)
;(js/setTimeout #(swap! server-state assoc :x 42) 5000)

(defonce page-state (r/atom {}))

(defonce client-state (r/atom {:debug true}))

(js/setInterval #(swap! client-state
                       (fn [s] (merge s {:timestamp (js/moment)}))) 1000)

