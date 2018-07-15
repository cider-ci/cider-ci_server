(ns cider-ci.server.front.requests.pages.requests
  (:refer-clojure :exclude [str keyword send-off])
  (:require
    [cider-ci.server.front.shared :refer [humanize-datetime-component short-id]]
    [cider-ci.server.front.breadcrumbs :as breadcrumbs]
    [cider-ci.server.front.requests.shared :as shared]
    [cider-ci.server.front.state :as state]
    [cider-ci.server.paths :refer [path]]

    [cider-ci.utils.core :refer [str keyword deep-merge presence]]


    [clojure.pprint :refer [pprint]]
    ))

(defn debug-component []
  (if-not (:debug @state/global-state*)
    [:div ]
    [:div.requests.debug
     [:hr]
     [:h2 "Requests Debug"]
     [:div.fetching?*
      [:h3 "@fetching?*"]
      [:pre (with-out-str (pprint @shared/fetching?*))] ]
     [:div.requests*
      [:h3 "@requests*"]
      [:pre (with-out-str (pprint @shared/state*))]]]))

(defn requests-head-row []
  [:tr
   [:th "Id"]
   [:th "Status"]
   [:th "Requested"]
   [:th "Key"]
   [:th "Title"]
   [:th "Actions"]])

(defn request-row [request]
  (let [status (shared/status request)
        bootstrap-status (shared/bootstrap-status status)]
    [:tr {:class (str "table-" bootstrap-status) :key (:key request)}
     [:td [:a {:href (path :request {:id (:id request)})}
           [short-id (:id request)]]]
     [:td status]
     [:td (humanize-datetime-component (:requested_at request))]
     [:td (-> request :meta :handler-key)]
     [:td (or (-> request :meta :title) (-> request :id))]
     [:td [shared/dismiss-button-component request {:class "btn-sm"}]]]))

(defn requests-component []
  [:table.table-sm.table-bordered.requests
   [:thead [requests-head-row]]
   [:tbody
    (doall
      (for [[_ request] (->> @shared/state* :requests (sort-by :requested_at) reverse)]
        [request-row request]
        ))]])

(defn page []
  [:div.page.requests
   [:div.row
    [:nav.col-lg {:aria-label :breadcrumb :role :navigation}
     [:ol.breadcrumb
      [breadcrumbs/home-li]
      [breadcrumbs/admin-li]
      [breadcrumbs/debug-li]
      [breadcrumbs/requests-li]]]
    [:nav.col-lg {:role :navigation}]]
   [:div.title
    [:h1 "Requests"]
    [:p "This page shows the status of the ongoing and recently finished HTTP requests. "
     "It is useful for debugging connection problems."]]
   [requests-component]
   [debug-component]])
