; Copyright © 2013 - 2017 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.server.users.api-tokens.ui.revocation
  (:refer-clojure :exclude [str keyword])
  (:require-macros
    [reagent.ratom :as ratom :refer [reaction]])
  (:require
    [cider-ci.server.client.connection.request :as request]
    [cider-ci.server.client.state :as state]
    [cider-ci.server.client.routes :as routes]
    [cider-ci.utils.core :refer [keyword str presence]]
    ))


(defn reset-state []
  (swap! state/page-state assoc-in [:current-page :show-revoke] false))

(defn toggle-revoke []
  (swap! state/page-state
         update-in [:current-page :show-revoke]
         (fn [b] (not b))))

(defn revoke []
  (request/send-off
    {:url (routes/user-api-token-path
            {:user-id (-> @state/page-state :current-page :user-id)
             :api-token-id (-> @state/page-state :current-page :api-token-id)})
     :method :patch
     :json-params {:revoked true}}
    {:title "Revoke a API-token"}
    :callback (fn [resp]))
  (toggle-revoke))

(defn revoke-modal-component [api-token*]
  [:div
   [:div.modal {:style {:display "block"}}
    [:div.modal-dialog
     [:div.modal-content.modal-danger
      [:div.modal-header
       [:h4 "Revoke the API-token " [:code (:token_part @api-token*)]]]
      [:div.modal-body
       [:p "Revoked API-tokens are \"frozen\". Revocations can not be undone! "]]
      [:div.modal-footer
       [:button.btn.btn-default.pull-left
        {:on-click toggle-revoke}
        "Cancel"]
       [:button.btn.btn-danger
        {:on-click revoke}
        [:i.fa.fa-delete]
        "Revoke" ]]]]]
   [:div.modal-backdrop {:style {:opacity "0.5"}}]])



