; Copyright © 2013 - 2018 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.server.executors.front.show
  (:refer-clojure :exclude [str keyword])
  (:require-macros
    [reagent.ratom :as ratom :refer [reaction]]
    [cljs.core.async.macros :refer [go]])
  (:require
    [cider-ci.server.executors.front.breadcrumbs :as executor-breadcrumbs]
    [cider-ci.server.front.breadcrumbs :as breadcrumbs]
    [cider-ci.server.front.requests.core :as requests]
    [cider-ci.server.front.shared :as front-shared]
    [cider-ci.server.front.shared :as shared :refer [humanize-datetime-component name->key]]
    [cider-ci.server.front.state :as state :refer [routing-state*]]
    [cider-ci.server.paths :as paths :refer [path]]
    [cider-ci.server.resources.api-token.front :as api-token]
    [cider-ci.server.resources.auth.front :as auth]

    [cider-ci.utils.core :refer [keyword str presence]]
    [cider-ci.utils.seq :refer [with-index]]

    [accountant.core :as accountant]
    [cljs.pprint :refer [pprint]]
    [cljs.core.async :as async]
    [cljs.core.async :refer [timeout]]
    [reagent.core :as reagent]))

(defonce executor-data* (reagent/atom nil))

(defonce executor-id* (reaction (-> @state/routing-state* :route-params :executor-id)))

(defn fetch-executor [& args]
  (defonce fetch-executor-id* (reagent/atom nil))
  (let [resp-chan (async/chan)
        id (requests/send-off {:url (path :executor {:executor-id @executor-id*})
                               :method :get}
                              {:modal false
                               :title "Fetch executor"
                               :retry-fn #'fetch-executor}
                              :chan resp-chan)]
    (reset! fetch-executor-id* id)
    (go (let [resp (<! resp-chan)]
          (when (and (= (:status resp) 200) ;success
                     (= id @fetch-executor-id*)) ;still the most recent request
            (<! (timeout 1000))
            #(when (and (= id @fetch-executor-id*)
                        (= :executor (:handler-key @state/routing-state*)))
               (fetch-executor)))))))

(defn reset-and-fetch []
  (reset! executor-data* nil)
  (fetch-executor)) 

(defn debug-component []
  (when (:debug @state/global-state*)
    [:div
     [:h3 "Page debug"]
     [:pre (with-out-str (pprint @executor-data*))]]))

(defn page []
  [:div.executors-page
   [state/hidden-routing-state-component
    {:did-mount reset-and-fetch 
     :did-change reset-and-fetch}]
   (breadcrumbs/nav-component
     [(breadcrumbs/home-li)
      (executor-breadcrumbs/executors-li)
      (executor-breadcrumbs/executor-li :id)]
     [])
   [:h1 " Executor \"" 
    (or (:name @executor-data*)
        (-> @state/routing-state* :route-params :executor-id))
    "\""]
   [:div.data
    [:h2 "Row Data"]
    (if-not @executor-data*
      [front-shared/please-wait-component]
      [:pre (with-out-str (pprint @executor-data*))])]
   [debug-component]])
