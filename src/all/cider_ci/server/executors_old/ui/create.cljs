; Copyright © 2013 - 2017 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.server.executors-old.ui.create
  (:refer-clojure :exclude [str keyword])
  (:require-macros
    [reagent.ratom :as ratom :refer [reaction]]
    )
  (:require
    [cider-ci.server.client.connection.request :as request]
    [cider-ci.server.client.routes :as routes]
    [cider-ci.server.client.state :as state]
    [cider-ci.server.executors-old.ui.form :as form]
    [cider-ci.utils.core :refer [keyword str presence]]
    [fipp.edn :refer [pprint]]
    [accountant.core :as accountant]
    [reagent.core :as r]
    ))

(def created? (reaction (= 201 (-> @state/client-state
                                   :executor :response
                                   :status))))

(defn reset []
  (form/reset-executor-form-data)
  (swap! state/client-state
         assoc-in [:executor :response] nil))

(defn create! []
  (request/send-off
    {:url (routes/executors-path)
     :method :post
     :json-params @form/form-data* }
    {:title "Add a new Executor"}
    :callback (fn [resp]
                (swap! state/client-state
                       assoc-in [:executor :response] resp))))

(defn continue []
  (let [user-id (-> @state/page-state :current-page :user-id)
        token-id (-> @state/client-state :executor :response :body :id) ]
    (reset)
    (accountant/navigate! (routes/executor-path
                            {:user-id user-id :executor-id token-id}))))

(defn show-secret-modal []
  [:div
   [:div.modal {:style {:display "block"}}
    [:div.modal-dialog
     [:div.modal-content.modal-success
      [:div.modal-header
       [:h4 "A new Executor has been created!"]]
      [:div.modal-body
       [:p.token [:code.text-center {:style {:font-size "1.5em"}}
            (-> @state/client-state :executor :response :body :token)]]
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
   [:div.pull-left
    [:a.btn.btn-warning
     {:href (routes/executors-path)}
     "Back"]]
   [:div.pull-right
    [:button.btn.btn-primary
     {:href "#"
      :disabled (not @form/form-valid*?)
      :on-click create!}
     "Add"]]
   [:div.clearfix]])

(defn debug-component []
  [:div
   [:hr]
   [:h2 "Debug Create"
    [:pre (with-out-str (pprint {:form/form-valid*? @form/form-valid*?
                                 :form/form-data* @form/form-data*
                                 }))]
    ]])

(defn orientation-component []
  [:div.orientation.row
   [:div.col-xs-6
    [:ol.breadcrumb.pull-left
     [:li [:a {:href (routes/executors-path)} "Executors" ]]]]
   [:div.col-xs-6
    [:ol.breadcrumb.pull-right]]])

(defn page-component []
  [:div
   [orientation-component]
   [:h1 "Add a new Executor"]
   (when @created?
     [show-secret-modal])
   [show-form]
   (when (-> @state/client-state :debug)
     [debug-component])])

(defn ^:export page []
  (r/create-class
    {:component-did-mount reset
     :reagent-render page-component}))


