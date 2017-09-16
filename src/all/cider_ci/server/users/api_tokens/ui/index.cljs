; Copyright © 2013 - 2017 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.server.users.api-tokens.ui.index
  (:refer-clojure :exclude [str keyword])
  (:require-macros
    [reagent.ratom :as ratom :refer [reaction]]
    )
  (:require
    [accountant.core :as accountant]
    [cider-ci.server.client.connection.request :as request]
    [cider-ci.server.client.routes :as routes]
    [cider-ci.server.client.state :as state]
    [cider-ci.server.client.utils :refer [humanize-datetime-component]]
    [cider-ci.server.client.utils :refer [humanize-datetime]]
    [cider-ci.server.users.api-tokens.ui.shared :refer [boolean-value-component reload-tokens user-id*]]
    [cider-ci.utils.core :refer [keyword str presence]]
    [reagent.core :as reagent]
    ))

(def api-tokens*
  (reaction
    (-> @state/client-state :users (get (keyword @user-id*)) :api-tokens)))

(defn create-new-component []
  [:div.pull-right
   [:a.btn.btn-primary
    {:href (routes/user-api-tokens-create-path
             {:user-id @user-id*
              :api-token-id "00000000-0000-0000-0000-000000000000"})}
    "Create a New API-Token"]])

(defn page-component []
  [:div
   [create-new-component]
   [:h1 "API-Tokens"]
   [:table.table.table-striped
    [:thead
     [:tr
      [:td "First characters"]
      [:td "Revoked"]
      [:td "Created"]
      [:td "Expires"]
      [:td "Description"]]]
    [:tbody
     (doall (for [token (->> @api-tokens*
                             (map (fn [[_ t]] t))
                             (sort-by (fn [t] (-> t :created_at js/moment .unix -))))]
       [:tr {:key (:id token)}
        [:td {:style {:font-family "monospace"}}
         [:a {:href (routes/user-api-token-path {:user-id @user-id*
                                                 :api-token-id (:id token)})}
          [:span (:token_part token)]]]
        [:td (boolean-value-component (:revoked token))]
        [:td.created_at (humanize-datetime (:timestamp @state/client-state)
                                           (:created_at token))]
        [:td.expires_at (humanize-datetime-component (:expires_at token))]
        [:td.description (:description token)]]))]]])

(defn ^:export page []
  (reagent/create-class
    {:component-did-mount reload-tokens
     :reagent-render page-component}))
