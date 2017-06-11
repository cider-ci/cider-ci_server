(ns cider-ci.server.ui2.ui.debug
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

(secretary/defroute path (str CONTEXT "/debug") []
  (swap! state/page-state assoc :current-page
         {:component #'page}))

(defn toggle-debug []
  (swap! state/client-state
         (fn [cs]
           (assoc cs :debug (not (:debug cs))))))

(defn page []
  [:div
   [:h1 "Debug"]
   [:p [:input {:style {:margin-left "1em"}
                :type "checkbox"
                :on-change toggle-debug
                :checked (:debug @state/client-state)
                }] " Debug state"]])
