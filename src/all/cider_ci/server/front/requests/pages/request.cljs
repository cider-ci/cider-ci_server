(ns cider-ci.server.front.requests.pages.request
  (:refer-clojure :exclude [str keyword send-off])
  (:require
    [clojure.pprint :refer [pprint]]
    [cider-ci.server.front.breadcrumbs :as breadcrumbs]
    [cider-ci.server.front.requests.shared :as shared]
    [cider-ci.server.front.state :as state]
    ))

(defn page []
  [:div.page.request
   [:div.row
    [:nav.col-lg {:aria-label :breadcrumb :role :navigation}
     [:ol.breadcrumb
      [breadcrumbs/home-li]
      [breadcrumbs/admin-li]
      [breadcrumbs/debug-li]
      [breadcrumbs/requests-li]
      [breadcrumbs/request-li {:id "x"}]]]
    [:nav.col-lg {:role :navigation}]]
   (let [id (-> @state/routing-state* :route-params :id)
         request (-> @shared/state* :requests (get id nil))]
     [:div.request
      [:div.title
       [:h1 " Request "
        [:span {:style {:font-family :monospace}} (->> id (take 8) clojure.string/join)]]]
      [:pre [:code (with-out-str (pprint request))]]])])
