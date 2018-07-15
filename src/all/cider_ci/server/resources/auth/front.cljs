(ns cider-ci.server.resources.auth.front
  (:refer-clojure :exclude [str keyword])
  (:require-macros
    [reagent.ratom :as ratom :refer [reaction]]
    [cljs.core.async.macros :refer [go]])
  (:require
    [cider-ci.server.anti-csrf.core :as anti-csrf]
    [cider-ci.server.front.breadcrumbs :as breadcrumbs]
    [cider-ci.server.front.components :as components]
    [cider-ci.server.front.icons :as icons]
    [cider-ci.server.front.requests.core :as requests]
    [cider-ci.server.front.shared :refer [humanize-datetime-component short-id gravatar-url]]
    [cider-ci.server.front.state :as state]
    [cider-ci.server.paths :as paths :refer [path]]

    [cider-ci.utils.core :refer [keyword str presence]]

    [accountant.core :as accountant]
    [cljs.core.async :as async]
    [cljs.core.async :refer [timeout]]
    [cljs.pprint :refer [pprint]]
    [clojure.contrib.inflect :refer [pluralize-noun]]
    [reagent.core :as reagent]
    ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def password-sign-in* (reagent/atom {}))

(def email* (reagent/atom nil))

(defn sign-in-nav-component []
  [:form.form-inline.my-2.my-lg-0
   {:on-submit (fn [e]
                 (.preventDefault e)
                 (accountant/navigate! (path :auth-sign-in)))}
   [:button.btn 
    [:span 
     [:span "Sign in "]
     " "
     icons/sign-in]]])
   
(defn password-sign-in-component-old []
  [:div
   [:h2 "Sign in with password"]
   (when (-> @state/routing-state* :query-params :sign-in-warning)
     [:div.alert.alert-warning
      [:h3 "Attention"]
      [:p "Make sure that you use the "
       [:b "correct email-address "]
       "and "
       [:b "the correct password. "]]
      [:p "Reset your password or contact your cider-ci.serveristrator if "
       "sign-in fails persistently." ]])
   [:form.form
    {:method :post
     :action (path :auth-password-sign-in)}
    [anti-csrf/hidden-form-group-token-component]
    [:div.form-group
     [:input
      {:name :url
       :type :hidden
       :value (-> @state/routing-state* :url)}]]
    [:div.form-group
     [:label {:for :email} "Email: "]
     [:div
      [:input.form-control
       {:id :email
        :name :email
        :type :email
        :value (:email @password-sign-in*)
        :on-change #(swap! password-sign-in* assoc :email (-> % .-target .-value presence))}]]]
    [:div.form-group
     [:label {:for :password} "Password: "]
     [:div
      [:input.form-control
       {:id :password
        :name :password
        :type :password
        :value (:password @password-sign-in*)
        :on-change #(swap! password-sign-in* assoc :password (-> % .-target .-value presence))}]]]
    [:div.form-group.float-right
     [:button.btn.btn-primary
      {:type :submit}
      [:i.fas.fa-sign-in-alt] " Sign in"]]]
   [:div.clearfix]])

(defn shib-sign-in-component []
  (when (-> @state/settings* :shibboleth_enabled)
    [:div
     [:h2 "Sign in via Shibboleth / SwitchAAI"]
     [:div.float-right
      [:a.btn.btn-primary {:href (-> @state/settings* :shibboleth_login_path)}
       [:i.fas.fa-sign-in-alt] " Sign in via Shibboleth / SwitchAAI"]]
     [:div.clearfix]]))

(defn sign-in-form-component []
  [:div
   [shib-sign-in-component]
   ;[password-sign-in-component]
   ])

(defn password-sign-in-page []
  (reagent/create-class
    {:component-did-mount #(reset! password-sign-in* {})
     :reagent-render
     (fn [_]
       [:div.password-sign-in
        (breadcrumbs/nav-component
          [(breadcrumbs/home-li)
           (breadcrumbs/admin-li)
           (breadcrumbs/auth-li)
           (breadcrumbs/auth-password-sign-in-li)
           ][])
        [sign-in-form-component]
        ])}))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defonce sign-in-data* (reagent/atom {}))

(def password* (reagent/atom nil))

(defn password-sign-in-component []
  (let []
    (when (:password_sign_in_enabled @sign-in-data*)
      [:div
       [:h2 "Sign in with Password"]
       [:form.form
        {:method :post
         :action (path :auth-password-sign-in)}
        [anti-csrf/hidden-form-group-token-component]
        [:div.form-group
         [:input.form-control
          {:id :email
           :name :email
           :type :hidden
           :value (-> @state/routing-state* :query-params :email)}]]
        [:div.form-group
         [:label {:for :password} "Password: "]
         [:input.form-control
          {:id :password
           :name :password
           :type :password
           :value @password* 
           :on-change #(reset! password* (-> % .-target .-value presence))}]]
        [:div.form-group.float-right
         [:button.btn.btn-primary
          {:type :submit}
          " Sign in with password "
          [:i.fas.fa-sign-in-alt]]]
        [:div.clearfix]]])))

(defn fetch-sing-in [& args]
  (defonce fetch-sing-in-id* (reagent/atom nil))
  (let [resp-chan (async/chan)
        id (requests/send-off {:url (path :sign-in)
                               :method :get
                               :query-params (:query-params @state/routing-state*)}
                              {:modal true
                               :title "Fetch Sign-In"
                               :retry-fn #'fetch-sing-in}
                              :chan resp-chan)]
    (reset! fetch-sing-in-id* id)
    (go (let [resp (<! resp-chan)]
          (when (and (= (:status resp) 200)
                     (= id @fetch-sing-in-id*))
            (reset! sign-in-data* (:body resp)))))))

(defn sign-in-debug-componnent []
  (when (:debug @state/global-state*)
    [:div.sign-in-debug
     [:hr]
     [:h2 "Sign-In Debug"]
     [:div.sign-in-data
      [:h3 "@sign-in-data*"]
      [:pre (with-out-str (pprint @sign-in-data*))]]]))

(defn sign-in-data-warning-component []
  (when (= false (:sign_in_enabled @sign-in-data*))
    [:div
     [:div.alert.alert-warning
      "Either no account for this email was found or sign-in is disabled. "
      "Verify the provided email address and try again. "
      "Contact an administrator if the problem persists. " ]]))

(defn sign-in-data-form-component []
  (when-not (:sign_in_enabled @sign-in-data*)
    [:div
     [:form.form
      {:on-submit (fn [e]
                    (.preventDefault e)
                    (fetch-sing-in))}
      [:div.form-group
       [:label {:for :email} "Email: "]
       [:input.form-control
        {:id :email
         :type :email
         :value (or (-> @state/routing-state* :query-params :email presence) "")
         :on-change #(accountant/navigate! 
                       (path (:handler-key @state/routing-state*) 
                             (:route-params @state/routing-state*)
                             (assoc (:query-params @state/routing-state*)
                                    :email (-> % .-target .-value presence)
                                    )))}]]
      [:div.form-group.float-right
       [:button.btn.btn-primary
        {:type :submit}
        " Continue to sign in "
        [:i.fas.fa-sign-in-alt]]]]
     [:div.clearfix]]))

(defn sign-in-page []
  [:div.sign-in
   [:h1 "Sign-In"]
   [sign-in-data-warning-component]
   [sign-in-data-form-component]
   [password-sign-in-component]
   [sign-in-debug-componnent]])


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defonce auth-data* (reagent/atom nil))

(def fetch-auth-id* (reagent/atom nil))

(defn fetch-auth []
  (reset! auth-data* nil)
  (let [resp-chan (async/chan)
        id (requests/send-off {:url (path :auth-info)
                               :method :get
                               :query-params {}}
                              {:modal false
                               :title "Fetch Authentication"
                               :handler-key :auth
                               :retry-fn #'fetch-auth}
                              :chan resp-chan)]
    (reset! fetch-auth-id* id)
    (go (let [resp (<! resp-chan)]
          (when (and (= (:status resp) 200)
                     (= id @fetch-auth-id*))
            (reset! auth-data* (:body resp)))))))


(defn info-page []
  (reagent/create-class
    {:component-did-mount #(fetch-auth)
     :reagent-render
     (fn [_]
       [:div.session
        (breadcrumbs/nav-component
          [(breadcrumbs/home-li)
           (breadcrumbs/admin-li)
           (breadcrumbs/auth-li)]
          [(breadcrumbs/auth-password-sign-in-li)])
        [:h1 "Authentication"]
        [:p "The data shown below is mostly of interest for exploring the API or for debugging."]
        (when-let [auth-data @auth-data*]
          [:pre.bg-light
           (with-out-str (pprint auth-data))])])}))


