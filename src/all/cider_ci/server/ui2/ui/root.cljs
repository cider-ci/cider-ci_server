(ns cider-ci.server.ui2.ui.root
  (:require-macros
    [cljs.core.async.macros :as asyncm :refer (go go-loop)]
    [reagent.ratom :as ratom :refer [reaction]]
    )
  (:require
    [cider-ci.server.ui2.constants :refer [CONTEXT]]
    [cider-ci.utils.core :refer [presence]]
    [cider-ci.server.client.state :as state]
    [secretary.core :as secretary :include-macros true]
    ))

(declare page)

(secretary/defroute path (str CONTEXT "/") []
  (swap! state/page-state assoc :current-page
         {:component #'page}))

(defn page []
  [:div.root

   [:hr]

   [:div.about.text-center
    [:h1 "About Cider-CI"]
    [:p "Cider-CI is an application and service stack for " [:b "highly parallelized" ] " and " [:b "resilient integration testing."]]
    [:p "Read more about Cider-CI at " [:a {:href "http://cider-ci.info"} "cider-ci.info"] "."]
    ]]

   )
