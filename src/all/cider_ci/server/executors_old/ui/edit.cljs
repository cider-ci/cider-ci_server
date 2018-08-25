; Copyright © 2013 - 2017 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.server.executors-old.ui.edit
  (:refer-clojure :exclude [str keyword])
  (:require-macros
    [reagent.ratom :as ratom :refer [reaction]]
    )
  (:require
    [cider-ci.server.client.connection.request :as request]
    [cider-ci.server.client.routes :as routes]
    [cider-ci.server.client.state :as state]

    [cider-ci.server.executors-old.shared :refer [allowed-keys]]
    [cider-ci.server.executors-old.ui.form :as form]
    [cider-ci.server.executors-old.ui.shared :refer [reload-executors executor-not-found-component]]

    [cider-ci.utils.core :refer [keyword str presence]]
    [fipp.edn :refer [pprint]]
    [accountant.core :as accountant]
    [reagent.core :as r]
    ))


(def executor-id*
  (reaction (-> @state/page-state
                :current-page :executor-id presence)))

(def executor*
  (reaction
    (when @executor-id*
      (-> @state/client-state :executors
          (get (keyword @executor-id*))))))

(defn debug-component []
  (when (:debug @state/client-state)
    [:div
     [:h1 "DEBUG Executor"]
     [:pre (with-out-str (pprint [@executor-id* @executor*]))]]))

(def updated? (reaction (= 201 (-> @state/client-state
                                   :executor :response
                                   :status))))

(defn reset []
  (form/reset-executor-form-data)
  (swap! state/client-state
         assoc-in [:executor :response] nil)
  (reload-executors
    :callback (fn [_]
                (form/reset-executor-form-data
                  @executor*))))

(defn update! []
  (request/send-off
    {:url (routes/executor-path {:executor-id @executor-id*})
     :method :patch
     :json-params @form/form-data* }
    {:title "Update an Executor"}
    :callback (fn [resp]
                (swap! state/client-state assoc-in [:executor :response] resp)
                (accountant/navigate!
                  (routes/executor-path {:executor-id @executor-id*})))))

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
       [:p [:code.text-center {:style {:font-size "1.5em"}}
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
      :on-click update!}
     "Update"]]
   [:div.clearfix]])

(defn orientation-component []
  [:div.orientation.row
   [:div.col-xs-6
    [:ol.breadcrumb.pull-left
     [:li [:a {:href (routes/executors-path)} "Executors" ]]
     [:li [:a {:href (routes/executor-path {:executor-id @executor-id*})} "Executor" ]]
     [:li.active  "Edit" ]
     ]]])

(defn page-component []
  [:div
   [orientation-component]
   (if @executor*
     [:div
      [:h1 "Edit Executor "
       [:span  (:name @executor*)]]
      (when @updated?
        [show-secret-modal])
      [show-form]]
     [executor-not-found-component executor-id*])
   (when (-> @state/client-state :debug)
     [debug-component])])

(defn ^:export page []
  (r/create-class
    {:component-did-mount reset
     :reagent-render page-component}))
