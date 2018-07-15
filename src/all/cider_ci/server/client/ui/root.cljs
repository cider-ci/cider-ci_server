(ns cider-ci.server.client.ui.root
  (:require-macros
    [cljs.core.async.macros :as asyncm :refer (go go-loop)]
    [reagent.ratom :as ratom :refer [reaction]]
    )
  (:require
    [cider-ci.server.client.constants :refer [CONTEXT]]
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
   [:div {:dangerouslySetInnerHTML
          {:__html (-> @state/page-state :root-page :welcome-message)}}]
   [:hr]
   [:div {:dangerouslySetInnerHTML
          {:__html (-> @state/page-state :root-page :about-message)}}]
   [:hr]
   [:div {:dangerouslySetInnerHTML
          {:__html (-> @state/page-state :root-page :about-release)}}]])
