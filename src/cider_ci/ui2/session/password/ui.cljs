(ns cider-ci.ui2.session.password.ui
  (:refer-clojure :exclude [str keyword])
  (:require
    [cider-ci.ui2.constants :refer [CONTEXT]]
    [cider-ci.ui2.shared :refer [anti-forgery-token]]
    [cider-ci.client.state :as state]

    [cider-ci.utils.core :refer [keyword str presence]]

    [secretary.core :as secretary :include-macros true]
    ))

(declare sign-in-page)

(secretary/defroute sign-in-path (str CONTEXT "/session/password/sign-in") [query-params]
  (swap! state/page-state assoc :current-page
         {:component #'sign-in-page
          :query-params query-params}))

(defn sign-in-form []
  [:form.password-sign-in
   {:action (str CONTEXT "/session/password/sign-in" )
    :method :post}
   [:input {:type :hidden
            :name :cider-ci_anti-forgery-token
            :value (anti-forgery-token) }]
   (when-let [url(-> @state/page-state :current-page
                     :query-params :url)]
     [:input {:type :hidden
              :name :url
              :value url}])
   [:div.form-group
    [:label {:for "login"} "Login / email address"]
    [:input#login.form-control
     {:type :text
      :placeholder "Login or email address"
      :autofocus :autofocus
      :name :login}]]
   [:div.form-group
    [:label {:for :password} "Password"]
    [:input#password.form-control
     {:type :password
      :placeholder "Password"
      :name :password}]]
   [:div.clearfix
    [:div.pull-right
     [:button.btn.btn-primary
      {:type :submit}
      [:i.fa.fa-fw.fa-sign-in]
      " Sign me in "]]]])

(defn sign-in-page []
  [:div.sign-in-page
   [:h1 "Sign in"]
   (when-let [error-message (-> @state/page-state :current-page
                                :query-params :error-message)]
     [:div.alert.alert-danger
      (js/decodeURIComponent error-message)])
   [sign-in-form]])
