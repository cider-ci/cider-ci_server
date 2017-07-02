; Copyright © 2013 - 2017 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.server.executors.ui.index
  (:refer-clojure :exclude [str keyword])
  (:require-macros
    [reagent.ratom :as ratom :refer [reaction]]
    )
  (:require
    [accountant.core :as accountant]
    [cider-ci.server.client.request :as request]
    [cider-ci.server.client.routes :as routes]
    [cider-ci.server.client.state :as state]
    [cider-ci.server.client.utils :refer [humanize-datetime humanize-datetime-component]]
    [cider-ci.server.executors.ui.shared :refer [reload-executors]]
    [cider-ci.utils.core :refer [keyword str presence]]
    [reagent.core :as reagent]
    ))


(def executors*
  (reaction
    (-> @state/client-state :executors )))


(defn orientation-component []
  [:div.orientation.row
   [:div.col-xs-6
    [:ol.breadcrumb.pull-left
     [:li [:a {:href (routes/executors-path)} "Executors" ]]]]
   [:div.col-xs-6
    [:ol.breadcrumb.pull-right
     [:li [:a.btn.btn-primary.btn-xs
           {:href (routes/executors-create-path
                    {:executor-id "00000000-0000-0000-0000-000000000000"})}
           [:i.fa.fa-fw.fa-plus-circle] "Add a New Executor " ]]]]])

(defn executor-row-component [executor]
  [:tr.executor {:id (:id executor) :key (:id executor)}
   [:td.name {:key :name}
    [:a {:href (routes/executor-path {:executor-id (:id executor)})}
     (:name executor)]]])

(defn page-component []
  [:div.executors
   [orientation-component]
   [:h1 "Executors"]
   [:table.table.table-striped
    [:thead
     [:tr]]
    [:tbody
     (doall (for [executor (->> @executors*
                                (map (fn [[_ e]] e))
                                (sort-by (fn [e] (-> e :name))))]
              [executor-row-component executor]
              ))]]])

(defn ^:export page []
  (reagent/create-class
    {:component-did-mount reload-executors
     :reagent-render page-component }))
