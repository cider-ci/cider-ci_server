; Copyright © 2013 - 2017 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.users.api-tokens.ui.edit
  (:refer-clojure :exclude [str keyword])
  (:require-macros [reagent.ratom :as ratom :refer [reaction]])
  (:require
    [accountant.core :as accountant]
    [cider-ci.client.request :as request]
    [cider-ci.client.routes :as routes]
    [cider-ci.client.state :as state]
    [cider-ci.client.utils :refer [humanize-datetime-component]]
    [cider-ci.users.api-tokens.ui.form :as form]
    [cider-ci.users.api-tokens.ui.revocation :as revocation]
    [cider-ci.users.api-tokens.ui.shared :refer [boolean-value-component reload-tokens scopes timestamps user-id* api-token-id*]]
    [cider-ci.utils.core :refer [keyword str presence]]
    [fipp.edn :refer [pprint]]
    [reagent.core :as reagent]
    ))

(defn init-data []
  (if-let [token (-> @state/client-state :users (get (keyword @user-id*))
                     :api-tokens (get (keyword @api-token-id*)))]
    (swap! state/client-state
           assoc-in [:api-token :form]
           token)))

(defn init []
  (swap! state/client-state assoc-in [:api-token :form] nil)
  (reload-tokens :callback init-data))

(defn patch! []
  (request/send-off
    {:url (routes/user-api-token-path
            {:user-id (-> @state/page-state :current-page :user-id)
             :api-token-id @api-token-id* })
     :method :patch
     :json-params @form/form-data* }
    {:title "Patch a API-Token"}
    :callback (fn [resp]
                (init-data)
                (accountant/navigate!
                  (routes/user-api-token-path
                    {:user-id @
                     user-id* :api-token-id @api-token-id*})))))

(defn debug-component []
  (when (:debug @state/client-state)
    [:div.debug
     [:h2 "Debug Edit API-Token"]
     [:pre (with-out-str
             (pprint
               {:user-id* @user-id*
                :api-token-id* @api-token-id*
                :form/form-data* @form/form-data*}))]]))

(defn page-component []
  [:div
   [:h1 "Edit API-token"]
   (when @form/form-data*
     [:div
      [form/form-component]
      [:div.pull-right
       [:button.btn.btn-warning
        {:on-click patch!
         :disabled (not @form/form-valid*?)}
        "Update"]]])
   [debug-component]])

(defn page []
  (reagent/create-class
    {:component-did-mount init
     :render page-component}))
