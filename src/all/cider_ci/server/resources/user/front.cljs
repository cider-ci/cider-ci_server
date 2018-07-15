(ns cider-ci.server.resources.user.front
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
    [cljsjs.moment]
    [clojure.contrib.inflect :refer [pluralize-noun]]
    [reagent.core :as reagent]
    ))

(declare fetcher-user)
(defonce user-id* (reaction (-> @state/routing-state* :route-params :user-id)))
(defonce user-data* (reagent/atom nil))


(defonce edit-mode?*
  (reaction
    (and (map? @user-data*)
         (boolean ((set '(:user-edit :user-new))
                   (:handler-key @state/routing-state*))))))

(def fetch-user-id* (reagent/atom nil))
(defn fetch-user []
  (let [resp-chan (async/chan)
        id (requests/send-off {:url (path :user (-> @state/routing-state* :route-params))
                               :method :get
                               :query-params {}}
                              {:modal false
                               :title "Fetch User"
                               :handler-key :user
                               :retry-fn #'fetch-user}
                              :chan resp-chan)]
    (reset! fetch-user-id* id)
    (go (let [resp (<! resp-chan)]
          (when (and (= (:status resp) 200)
                     (= id @fetch-user-id*))
            (reset! user-data* (:body resp)))))))


;;; reload logic ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn clean-and-fetch [_]
  (reset! user-data* nil)
  (fetch-user))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn field-component
  ([kw]
   (field-component kw {}))
  ([kw opts]
   (let [opts (merge {:type :text} opts)]
     [:div.form-group
      [:label.col.col-form-label {:for kw} kw]
      [:input.form-control
       {:id kw
        :type (:type opts)
        :value (or (kw @user-data*) "")
        :on-change #(swap! user-data* assoc kw (-> % .-target .-value presence))
        :disabled (not @edit-mode?*)}]])))

(defn checkbox-component [kw]
  [:div.form-check.form-check-inline
   [:label {:for kw}
    [:input
     {:id kw
      :type :checkbox
      :checked (kw @user-data*)
      :on-change #(swap! user-data* assoc kw (-> @user-data* kw boolean not))
      :disabled (not @edit-mode?*)}]
    [:span.ml-2 kw]]])

(defn debug-component []
  (when (:debug @state/global-state*)
    [:div.user-debug
     [:hr]
     [:div.edit-mode?*
      [:h3 "@edit-mode?*"]
      [:pre (with-out-str (pprint @edit-mode?*))]]
     [:div.user-data
      [:h3 "@user-data*"]
      [:pre (with-out-str (pprint @user-data*))]]]))


;; user components ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn basic-component []
  [:div.form
   [checkbox-component :is_admin]
   [checkbox-component :sign_in_enabled]
   [checkbox-component :password_sign_in_enabled]
   [field-component :name]
   [field-component :primary_email_address]
   [field-component :password {:type :password}]
   ])


(defn additional-properties-component []
  [:div.additional-properties
   [:p [:span "The user has been created " [humanize-datetime-component (:created_at @user-data*)]
        ", and updated "[humanize-datetime-component (:updated_at @user-data*)]
        ". "]]])

(defn user-component []
  [:div.user-component
   (if (nil?  @user-data*)
     [:div.text-center
      [:i.fas.fa-spinner.fa-spin.fa-5x]
      [:span.sr-only "Please wait"]]
     [:div
      [basic-component]
      (when-not @edit-mode?*
        [additional-properties-component])])])

(defn name-component []
  (if-not @user-data*
    [:span {:style {:font-family "monospace"}} (short-id @user-id*)]
    [:em (or (-> @user-data* :name presence) 
             (-> @user-data* :primary_email_address))]))

(defn user-id-component []
  [:p "id: " [:span {:style {:font-family "monospace"}} (:id @user-data*)]])

;;; show ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn show-page []
  [:div.user
   [state/hidden-routing-state-component
    {:will-mount clean-and-fetch
     :did-change clean-and-fetch}]
   (breadcrumbs/nav-component
     [(breadcrumbs/home-li)
      (breadcrumbs/admin-li)
      (breadcrumbs/users-li)
      (breadcrumbs/user-li @user-id*)]
     [(breadcrumbs/api-tokens-li @user-id*)
      (breadcrumbs/email-addresses-li @user-id*)
      (breadcrumbs/gpg-keys-li @user-id*)
      (breadcrumbs/user-delete-li @user-id*)
      (breadcrumbs/user-edit-li @user-id*)
      ])
   [:div.row
    [:div.col-lg
     [:h1
      [:span " User "]
      [name-component]]
     [user-id-component]]]
   [user-component]
   [debug-component]])


;;; edit ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn patch [_]
  (let [resp-chan (async/chan)
        id (requests/send-off {:url (path :user {:user-id @user-id*})
                               :method :patch
                               :json-params  @user-data*}
                              {:modal true
                               :title "Update User"
                               :handler-key :user-edit
                               :retry-fn #'patch}
                              :chan resp-chan)]
    (go (let [resp (<! resp-chan)]
          (when (= (:status resp) 204)
            (accountant/navigate!
              (path :user {:user-id @user-id*})))))))

(defn patch-submit-component []
  (if @edit-mode?*
    [:div
     [:div.float-right
      [:button.btn.btn-warning
       {:on-click patch}
       [:i.fas.fa-save]
       " Save "]]
     [:div.clearfix]]))

(defn edit-page []
  [:div.edit-user
   [state/hidden-routing-state-component
    {:will-mount clean-and-fetch
     :did-change clean-and-fetch}]
   (breadcrumbs/nav-component
     [(breadcrumbs/home-li)
      (breadcrumbs/admin-li)
      (breadcrumbs/users-li)
      (breadcrumbs/user-li @user-id*)
      (breadcrumbs/user-edit-li @user-id*)][])
   [:div.row
    [:div.col-lg
     [:h1
      [:span " Edit User "]
      [name-component]]
     [user-id-component]]]
   [user-component]
   [patch-submit-component]
   [debug-component]])


;;; new  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn create [_]
  (let [resp-chan (async/chan)
        id (requests/send-off {:url (path :users)
                               :method :post
                               :json-params  @user-data*}
                              {:modal true
                               :title "Create User"
                               :handler-key :user-new
                               :retry-fn #'create}
                              :chan resp-chan)]
    (go (let [resp (<! resp-chan)]
          (when (= (:status resp) 200)
            (accountant/navigate!
              (path :user {:user-id (-> resp :body :id)})))))))

(defn create-submit-component []
  (if @edit-mode?*
    [:div
     [:div.float-right
      [:button.btn.btn-primary
       {:on-click create}
       " Create "]]
     [:div.clearfix]]))

(defn new-page []
  [:div.new-user
   [state/hidden-routing-state-component
    {:will-mount #(reset! user-data* {:sign_in_enabled true
                                      :password_sign_in_enabled true})}]
   (breadcrumbs/nav-component
     [(breadcrumbs/home-li)
      (breadcrumbs/admin-li)
      (breadcrumbs/users-li)
      (breadcrumbs/user-new-li)
      ][])
   [:div.row
    [:div.col-lg
     [:h1
      [:span " New User "]]]]
   [user-component]
   [create-submit-component]
   [debug-component]])


;;; delete ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def transfer-data* (reagent/atom {}))

(defn delete-user [_]
  (let [resp-chan (async/chan)
        id (requests/send-off {:url (path :user (-> @state/routing-state* :route-params))
                               :method :delete
                               :query-params {}}
                              {:title "Delete User"
                               :handler-key :user-delete
                               :retry-fn #'delete-user}
                              :chan resp-chan)]
    (go (let [resp (<! resp-chan)]
          (when (= (:status resp) 204)
            (accountant/navigate!
              (path :users {}
                    (-> @state/global-state* :users-query-params))))))))

(defn delete-without-reasignment-component []
  [:div.card.m-3
   [:div.card-header.bg-warning
    [:h2 "Delete User"]]
   [:div.card-body
    [:p.text-warning
     "If the to be deleted user is associated to other entities "
     "the delete operation might fail. It does so then without modifying data. "]
    [:div.float-right
     [:button.btn.btn-warning.btn-lg
      {:on-click delete-user}
      [:i.fas.fa-times] " Delete"]]]])


(defn delete-page []
  [:div.user-delete
   [state/hidden-routing-state-component
    {:will-mount clean-and-fetch
     :did-change clean-and-fetch}]
   [:div.row
    [:nav.col-lg {:aria-label :breadcrumb :role :navigation}
     [:ol.breadcrumb
      [breadcrumbs/home-li]
      [breadcrumbs/admin-li]
      [breadcrumbs/users-li]
      [breadcrumbs/user-li @user-id*]
      [breadcrumbs/user-delete-li @user-id*]]]
    [:nav.col-lg {:role :navigation}]]
   [:h1 "Delete User "
    [name-component]]
   [user-id-component]
   [:p.text-danger
    "Users should never be deleted! "
    "Instead it is recommended to " [:b " disable sign-in"]
    " via editing the user. "]
   [delete-without-reasignment-component]])
