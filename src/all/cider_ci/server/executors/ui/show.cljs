; Copyright © 2013 - 2017 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.server.executors.ui.show
  (:refer-clojure :exclude [str keyword])
  (:require-macros
    [reagent.ratom :as ratom :refer [reaction]]
    )
  (:require
    [cider-ci.server.client.routes :as routes]
    [cider-ci.server.client.request :as request]

    [cider-ci.server.client.state :as state]
    [cider-ci.server.executors.ui.shared :refer [reload-executors executor-not-found-component]]

    [fipp.edn :refer [pprint]]
    [reagent.core :as reagent]
    [accountant.core :as accountant]
    [cider-ci.utils.core :refer [keyword str presence]]
    [cider-ci.server.client.utils :refer [humanize-datetime-component]]
    ))


(def executor-id*
  (reaction (-> @state/page-state
                :current-page :executor-id presence)))

(def executor*
  (reaction
    (when @executor-id*
      (-> @state/client-state :executors
          (get (keyword @executor-id*))))))

(defn delete! []
  (request/send-off
    {:url (routes/executor-path {:executor-id @executor-id*})
     :method :delete}
    {:title "Delete an Executor"}
    :callback (fn [resp]
                (swap! state/client-state assoc-in [:executor :response] resp)
                (when (<= 200 (-> resp :status) 299)
                  (accountant/navigate! (routes/executors-path))))))

(defn reset []
  (reload-executors))

(defn debug-component []
  (when (:debug @state/client-state)
    [:div
     [:h1 "DEBUG Executor"]
     [:pre (with-out-str (pprint [@executor-id* @executor*]))]]))

(defn actions-component []
  [:div.btn-group
   [:a.btn.btn-primary
    {:href (routes/executor-edit-path
             {:executor-id @executor-id*})}
    [:i.fa.fa-pencil] " Edit"]])

(defn orientation-component []
  [:div.orientation.row
   [:div.col-xs-6
    [:ol.breadcrumb.pull-left
     [:li [:a {:href (routes/executors-path)} "Executors" ]]
     [:li.active "Executor" ]
     ]]
   (when @executor*
     [:div.col-xs-6
      [:ol.breadcrumb.pull-right
       [:li
        [:a.btn.btn-xs.btn-danger
         {:href "#"
          :on-click delete!}
         [:i.fa.fa-remove] " Delete "]]
       [:li
        [:a.btn.btn-xs.btn-primary
         {:href (routes/executor-edit-path
                  {:executor-id @executor-id*})}
         [:i.fa.fa-pencil] " Edit"]]]])])

(defn page-component []
  [:div
   [orientation-component]
   (if-let [executor @executor*]
     [:div
      [:h1 "Executor"]
      [:table.table.table-striped
       [:thead]
       [:tbody
        (doall (for [[k v] (->> executor
                                (sort-by (fn [[k v]] k))
                                )]
                 [:tr {:key k :id k}
                  [:td.key k]
                  [:td.value [:span {:style {:font-family :monospace}}
                              (with-out-str (pprint v))]]]))]]]
     [executor-not-found-component executor-id*])
   [debug-component]])


(defn ^:export page []
  (reagent/create-class
    {:component-did-mount reset
     :render (fn [] [page-component])}))
