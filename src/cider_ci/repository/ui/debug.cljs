(ns cider-ci.repository.ui.debug
  (:require
    [cider-ci.client.state :as state]
    ))


(defn page []
  [:section#debug-db
   [:h1 "Debug DB"]
   [:pre.code
    (.stringify js/JSON (clj->js @state/debug-db) nil 2)]])

