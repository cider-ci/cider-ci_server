(ns cider-ci.server.resources.status.front
  (:refer-clojure :exclude [str keyword])
  (:require-macros
    [reagent.ratom :as ratom :refer [reaction]]
    [cljs.core.async.macros :refer [go]])
  (:require
    [cider-ci.server.anti-csrf.core :as anti-csrf]
    [cider-ci.server.front.breadcrumbs :as breadcrumbs]
    [cider-ci.server.front.components :as components]
    [cider-ci.server.front.icons :as icons]
    [cider-ci.server.front.requests.core :as requests]
    [cider-ci.server.front.shared :refer [humanize-datetime-component short-id gravatar-url]]
    [cider-ci.server.front.state :as state]
    [cider-ci.server.paths :as paths :refer [path]]
    [cider-ci.utils.core :refer [keyword str presence]]

    [accountant.core :as accountant]
    [cljs.core.async :as async]
    [cljs.core.async :refer [timeout]]
    [cljs.pprint :refer [pprint]]
    [clojure.contrib.inflect :refer [pluralize-noun]]
    [reagent.core :as reagent]
    ))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defonce status-info-data* (reagent/atom nil))

(def fetch-status-info-id* (reagent/atom nil))

(defn fetch-status-info []
  ;(reset! status-info-data* nil)
  (let [resp-chan (async/chan)
        id (requests/send-off {:url (path :status)
                               :method :get
                               :query-params {}}
                              {:modal false
                               :title "Fetch Status-Info"
                               :handler-key :auth
                               :retry-fn #'fetch-status-info}
                              :chan resp-chan)]
    (reset! fetch-status-info-id* id)
    (go (let [resp (<! resp-chan)]
          (when (and (= :status (-> @state/routing-state* :handler-key)) 
                     (or (= (:status resp) 200)
                         (>= 900(:status resp)))
                     (= id @fetch-status-info-id*))
            (reset! status-info-data* (:body resp))
            (js/setTimeout  #(fetch-status-info) 1000))))))

(defn info-page []
  (reagent/create-class
    {:component-did-mount #(fetch-status-info)
     :reagent-render
     (fn [_]
       [:div.session
        (breadcrumbs/nav-component
          [(breadcrumbs/home-li)
           (breadcrumbs/li :status "Status-Info")]
          [])
        [:h1 "Server-Status Info"]
        [:p "The data shown below is mostly of interest for exploring the API or for debugging."]
        (when-let [status-info-data @status-info-data*]
          [:pre.bg-light
           (with-out-str (pprint status-info-data))])])}))

