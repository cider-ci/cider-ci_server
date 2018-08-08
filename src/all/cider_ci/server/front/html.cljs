(ns cider-ci.server.front.html
  (:refer-clojure :exclude [str keyword])
  (:require-macros
    [reagent.ratom :as ratom :refer [reaction]]
    )
  (:require
    [cider-ci.server.front.icons :as icons]
    [cider-ci.server.anti-csrf.core :as anti-csrf]
    [cider-ci.server.front.requests.core :as requests]
    [cider-ci.server.front.requests.modal]
    [cider-ci.server.front.shared :refer [humanize-datetime-component short-id gravatar-url]]
    [cider-ci.server.front.state :as state :refer [routing-state*]]
    [cider-ci.server.paths :refer [path]]
    [cider-ci.server.resources.home.front :as home]
    [cider-ci.server.resources.admin.front :as admin]
    [cider-ci.server.resources.auth.front :as auth]

    [cider-ci.utils.core :refer [keyword str presence]]

    [clojure.pprint :refer [pprint]]
    [accountant.core :as accountant]
    [reagent.core :as reagent]
    ))

(defn li-navitem [handler-key display-string]
  (let [active? (= (-> @state/routing-state* :handler-key) handler-key)]
    [:li.nav-item
     {:class (if active? "active" "")}
     [:a.nav-link {:href (path handler-key)} display-string]]))

(defn li-admin-navitem []
  (let [active? (boolean
                  (when-let [current-path (-> @state/routing-state* :path)]
                    (re-matches #"^/admin.*$" current-path)))]
    [:li.nav-item
     {:class (if active? "active" "")}
     [:a.nav-link {:href (path :admin)} "Admin"]]))


(defn sign-out-nav-component []
  [:form.form-inline.ml-2
   {:action (path :auth-sign-out {} {:target (-> @state/routing-state* :url)})
    :method :post}
   [:div.form-group
    [:input
     {:name :url
      :type :hidden
      :value (-> @state/routing-state* :url)}]]
   [anti-csrf/hidden-form-group-token-component]
   [:div.form-group
    [:label.sr-only
     {:for :sign-out}
     "Sign out"]
    [:button#sign-out.btn.btn-dark.form-group
     {:type :submit
      :style {:padding-top "0.2rem"
              :padding-bottom "0.2rem"}}
     [:span 
      [:span " Sign out "]
      [:i.fas.fa-sign-out-alt]]]]])

(defn navbar-user-nav []
  (if-let [user @state/user*]
    [:div.navbar-nav.user-nav
     [:div
      [:a
       {:href (path :user {:user-id (:id user)} {})}
       [:span
        [:img.user-img-32
         {:src (gravatar-url (:primary_email_address user))}]
        [:span.sr-only (:primary_email_address user)]]]]
     [sign-out-nav-component]]
    (when-not (= :auth-sign-in (:handler-key @routing-state*))
      [:div.navbar-nav
       [auth/sign-in-nav-component]])))

(defn nav-bar []
  [:nav.navbar.navbar-expand.navbar-dark.justify-content-between.bg-primary
   [:a.navbar-brand {:href (path :home)} "Cider-CI"]
   [:div
    [:ul.navbar-nav
     [li-admin-navitem]]]
   [navbar-user-nav]])

(defn current-page []
  [:div
   [cider-ci.server.front.requests.modal/modal-component]
   [nav-bar]
   [:div
    (if-let [page (:page @routing-state*)]
      [page]
      [:div.page
       [:h1.text-danger 
        "Error 404 - Page does not exist"]
       [:p "The current path can not be resolved!"]])]
   [state/debug-component]
   [:nav.footer.navbar.navbar-expand-lg.navbar-dark.bg-secondary.col
    {:style {:margin-top "3em"}}
    [:div.col
     [:a.navbar-brand {:href (path :home)} "Cider-CI"]
     [:span.navbar-text "Version 5.0.0 Alpha"]]
    [:div.col
     [:a.navbar-text 
      {:href (path :status)} "Status"]]
    [state/debug-toggle-navbar-component]
    [:form.form-inline {:style {:margin-left "0.5em"
                                :margin-right "0.5em"}}
     [:label.navbar-text
      [:a {:href (path :requests)}
       [requests/icon-component]
       " Requests "]]]]])

(defn mount []
  (when-let [app (.getElementById js/document "app")]
    (reagent/render [current-page] app))
  (accountant/dispatch-current!))
