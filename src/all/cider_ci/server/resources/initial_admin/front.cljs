(ns cider-ci.server.resources.initial-admin.front
  (:refer-clojure :exclude [str keyword])
  (:require-macros
    [reagent.ratom :as ratom :refer [reaction]]
    [cljs.core.async.macros :refer [go]])
  (:require
    [cider-ci.server.front.breadcrumbs :as breadcrumbs]
    [cider-ci.server.front.components :as components]
    [cider-ci.server.front.requests.core :as requests]
    [cider-ci.server.front.shared :refer [humanize-datetime-component short-id gravatar-url]]
    [cider-ci.server.front.state :as state]
    [cider-ci.server.paths :as paths :refer [path]]

    [cider-ci.utils.core :refer [keyword str presence]]

    [accountant.core :as accountant]
    [cljs.core.async :as async]
    [cljs.core.async :refer [timeout]]
    [cljs.pprint :refer [pprint]]
    [reagent.core :as reagent]
    ))

(def form-data* (reagent/atom {}))

(def email-valid*?
  (reaction
    (boolean
      (when-let [email (-> @form-data* :primary_email_address presence)]
        (re-matches #".+@.+" email)))))

(def password-valid*?
  (reaction
    (boolean (-> @form-data* :password presence))))

(def form-valid*? (reaction (and @email-valid*? @password-valid*? )))

(defn debug-component []
  (when (:debug @state/global-state*)
    [:div.debug
     [:hr]
     [:h3 "@form-data*"]
     [:pre (with-out-str (pprint @form-data*))]]))


(defn text-input-component
     ([kw]
      (text-input-component kw {}))
     ([kw opts]
      (let [opts (merge {:type :text
                         :valid* (reaction (-> @form-data* kw presence))}
                        opts)]
        [:div.form-group
         {:key kw}
         [:label {:for kw} kw]
         [:input.form-control
          {:type (:type opts)
           :class (if @(:valid* opts) "" "is-invalid")
           :value (or (-> @form-data* kw) "")
           :on-change #(swap! form-data* assoc kw (-> % .-target .-value presence))
           }]])))

(defn form-component []
  [:form.form
   {:method :post
    :action (path :initial-admin)}
   [:div.form-group
    [:label {:for :email} "email address"]
    [:div
     [:input.form-control
      {:id :primary_email_address
       :class (when-not @email-valid*? "is-invalid")
       :name :primary_email_address
       :type :email
       :autoComplete "email"
       :value (:primary_email_address @form-data*)
       :on-change #(swap! form-data* assoc :primary_email_address (-> % .-target .-value presence))}]]]
   [:div.form-group
    [:label {:for :password} "password"]
    [:div
     [:input.form-control
      {:id :password
       :class (when-not @email-valid*? "is-invalid")
       :name :password
       :type :password
       :autoComplete "new-password"
       :value (:password @form-data*)
       :on-change #(swap! form-data* assoc :password (-> % .-target .-value presence))}]]]

   [:div.form-group.float-right
    [:button.btn.btn-primary
     {:type :submit
      :disabled (not @form-valid*?)}
     "Create initial adminstrator"]]
   [:div.clearfix]])

(defn page []
  [:div.initial-admin
   [:div.row
    [:nav.col-lg {:aria-label :breadcrumb :role :navigation}
     [:ol.breadcrumb
      [breadcrumbs/home-li]
      [breadcrumbs/admin-li]
      [breadcrumbs/initial-admin-li]]]
    [:nav.col-lg {:role :navigation}]]
   [:div
    [:h1 "Initial Admin"]
    [:p "An initial administrator account is required to sign in and further configure this instance of Cider-CI"]
    [form-component]
    ]])
