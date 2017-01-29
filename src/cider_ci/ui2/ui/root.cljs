(ns cider-ci.ui2.ui.root
  (:require-macros
    [cljs.core.async.macros :as asyncm :refer (go go-loop)]
    [reagent.ratom :as ratom :refer [reaction]]
    )
  (:require
    [cider-ci.ui2.constants :refer [CONTEXT]]
    [cider-ci.utils.core :refer [presence]]
    [cider-ci.client.state :as state]
    [secretary.core :as secretary :include-macros true]
    ))

(declare page)

(secretary/defroute path (str CONTEXT "/") []
  (swap! state/page-state assoc :current-page
         {:component #'page}))

(defn page []
  [:div
   [:h1 "Root"]
   ])
