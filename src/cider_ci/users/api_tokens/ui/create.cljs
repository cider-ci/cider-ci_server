; Copyright © 2013 - 2017 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.users.api-tokens.ui.create
  (:refer-clojure :exclude [str keyword])
  (:require-macros
    [reagent.ratom :as ratom :refer [reaction]]
    )
  (:require
    [cider-ci.client.request :as request]
    [cider-ci.client.routes :as routes]
    [cider-ci.client.state :as state]
    [cider-ci.users.api-tokens.ui.form :as form]
    [cider-ci.utils.core :refer [keyword str presence]]
    [fipp.edn :refer [pprint]]
    [accountant.core :as accountant]
    [reagent.core :as r]
    ))

(def created? (reaction (= 201 (-> @state/client-state
                                   :api-token :response
                                   :status))))

(defn reset []
  (form/reset-api-token-form-data)
  (swap! state/client-state
         assoc-in [:api-token :response] nil))

(defn create! []
  (request/send-off
    {:url (routes/user-api-tokens-path
            {:user-id (-> @state/page-state :current-page :user-id)})
     :method :post
     :json-params @form/form-data* }
    {:title "Create New API-Token"}
    :callback (fn [resp]
                (swap! state/client-state
                       assoc-in [:api-token :response] resp))))

(defn continue []
  (let [user-id (-> @state/page-state :current-page :user-id)
        token-id (-> @state/client-state :api-token :response :body :id) ]
    (reset)
    (accountant/navigate! (routes/user-api-token-path
                            {:user-id user-id :api-token-id token-id}))))

(defn show-secret-modal []
  [:div
   [:div.modal {:style {:display "block"}}
    [:div.modal-dialog
     [:div.modal-content.modal-success
      [:div.modal-header
       [:h4 "A new API-token has been created!"]]
      [:div.modal-body
       [:p [:code.text-center {:style {:font-size "1.5em"}}
            (-> @state/client-state :api-token :response :body :secret)]]
       [:p "We store the first 5 characters of this token for your convenience."]
       [:p.text-warning
        "The full token is shown only once here and now! "
        "It can not be recovered later on."]]
      [:div.modal-footer
       [:button.btn.btn-primary
        {:on-click continue}
        "Continue" ]]]]]
   [:div.modal-backdrop {:style {:opacity "0.5"}}]])

(defn show-form []
  [:div
   [form/form-component]
   [:div.pull-right
    [:button.btn.btn-primary
     {:href "#"
      :disabled (not @form/form-valid*?)
      :on-click create!}
     "Create"]]
   [:div.clearfix]])

(defn debug-component []
  [:div
   [:hr]
   [:h2 "Debug Create"
    [:pre (with-out-str (pprint {:form/form-valid*? @form/form-valid*?
                                 :form/form-data* @form/form-data*
                                 }))]
    ]])

(defn page-component []
  [:div
   [:h1 "Create a New API-Token"]
   (when @created?
     [show-secret-modal])
   [show-form]
   (when (-> @state/client-state :debug)
     [debug-component])])

(defn ^:export page []
  (r/create-class
    {:component-did-mount reset
     :reagent-render page-component}))
