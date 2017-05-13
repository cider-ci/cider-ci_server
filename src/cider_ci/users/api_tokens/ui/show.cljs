; Copyright © 2013 - 2017 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.users.api-tokens.ui.show
  (:refer-clojure :exclude [str keyword])
  (:require-macros
    [reagent.ratom :as ratom :refer [reaction]]
    )
  (:require
    [fipp.edn :refer [pprint]]
    [cider-ci.users.api-tokens.ui.shared :refer [boolean-value-component reload-tokens scopes timestamps]]
    [cider-ci.client.routes :as routes]
    [cider-ci.client.state :as state]
    [reagent.core :as reagent]
    [cider-ci.utils.core :refer [keyword str presence]]
    [cider-ci.client.utils :refer [humanize-datetime-component]]
    [cider-ci.users.api-tokens.ui.revocation :as revocation]
    ))


(def user-id*
  (reaction (-> @state/page-state
                :current-page :user-id presence)))

(def api-token-id*
  (reaction (-> @state/page-state
                :current-page :api-token-id presence)))

(def api-token*
  (reaction
    (when (and @user-id* @api-token-id*)
      (-> @state/client-state :users
          (get (keyword @user-id*)) :api-tokens
          (get (keyword @api-token-id*))))))

(defn reset []
  (revocation/reset-state)
  (reload-tokens))


(defn debug-component []
  [:div
   [:h1 "DEBUG API-Token"]
   (when (:debug @state/client-state)
     [:pre (with-out-str (pprint [@user-id* @api-token-id* @api-token*]))]
     )])


(defn dates-component [api-token]
  (doall
    (for [at [:expires_at :created_at :updated_at]]
      [:tr {:key at :class at}
       [:td (->> (-> at str (clojure.string/split "_")) (take 1) (clojure.string/join " "))]
       [:td.value (humanize-datetime-component (-> api-token at))]
       ])))

(defn date-row-component [at api-token]
  [:tr {:key (str :tr at (:id api-token)) :class at}
   [:td {:key (str at :label)} (->> (-> at str (clojure.string/split "_")) (take 1) (clojure.string/join " "))]
   [:td.value {:key (str at :value)} (humanize-datetime-component (-> api-token at))]
   ])

(defn scope-component [scope api-token]
  [:tr {:key (str scope (:id api-token)) :class scope}
   [:td (->> (-> scope str (clojure.string/split "_")) (drop 1) (clojure.string/join " "))]
   [:td {:class (str scope)}
    (let [value (-> api-token scope)]
      [boolean-value-component value :class scope])]])

(defn actions-component []
  (when-not (:revoked @api-token*)
    [:div.btn-group
     [:button.btn.btn-warning
      {:on-click revocation/toggle-revoke}
      [:i.fa.fa-remove] " Revoke"]
     [:a.btn.btn-primary
      {:href (routes/user-api-token-edit-path
               {:user-id @user-id* :api-token-id @api-token-id*})}
      [:i.fa.fa-pencil] " Edit"]]))

(defn page-component []
  [:div
   (when (-> @state/page-state :current-page :show-revoke)
     [revocation/revoke-modal-component api-token*])
   [:div.pull-right
    [actions-component]
    [:div.clearfix]]
   [:h1 "API-Token"]
   (when-let [api-token @api-token*]
     [:table.table.table-striped
      [:thead]
      [:tbody
       [:tr.id
        [:td {:key :id} "id"]
        [:td.value.id {:key :value
                       :style {:font-family "monospace"}}
         (:id api-token)]]
       [:tr.token_part
        [:td "first characters"]
        [:td.value.token_part
         {:style {:font-family "monospace"}}
         (:token_part api-token)]]
       [:tr.description
        [:td "description"]
        [:td.value.description
         (:description api-token)]]
       [:tr.revoked
        [:td "revoked"]
        [:td.value.revoked [boolean-value-component (:revoked api-token)]]]
       (doall (for [scope scopes] [scope-component scope api-token]))
       (doall (for [at timestamps] [date-row-component at api-token]))]])])


(defn ^:export page []
  (reagent/create-class
    {:component-did-mount reset
     :render (fn [] [page-component])}))
