(ns cider-ci.ui2.create-admin.ui
  (:refer-clojure :exclude [str keyword])
  (:require-macros
    [reagent.ratom :as ratom :refer [reaction]]
    [cljs.core.async.macros :refer [go]]
    )
  (:require
    [cider-ci.ui2.shared :refer [pre-component]]
    [cider-ci.utils.core :refer [keyword str presence]]
    [cider-ci.ui2.constants :refer [CONTEXT]]
    [cider-ci.client.state :as state]
    [cider-ci.ui2.session.password.ui :as session.password]

    [reagent.core :as reagent]
    [secretary.core :as secretary :include-macros true]
    [cljs-http.client :as http]
    ))

(declare page)

(secretary/defroute create-admin-path (str CONTEXT "/create-admin") []
  (swap! state/page-state assoc :current-page
         {:component #'page}))

(def data (reaction (-> @state/client-state :create-admin)))
(def form-data (reaction (-> @data :form-data)))
(def response-pending?
  (reaction (and (-> @data :request presence boolean)
                 (-> @data :response presence boolean not))))
(def response-success?  (reaction (-> @data :response :success boolean)))
(def request? (reaction (-> @data :request presence boolean)))

;; post

(defn post-create-admin []
  (let [request {:url (str CONTEXT "/create-admin")
                 :method :post
                 :json-params (select-keys @form-data [:login :password])}]
    (swap! state/client-state
           assoc-in [:create-admin :request] request)
    (swap! state/client-state
           assoc-in [:create-admin :response] nil)
    (go (let [resp (<! (http/request request))]
          (swap! state/client-state
                 assoc-in [:create-admin :response] resp)))))

(defn response-success-component []
  (let [redirect-in (reagent/atom 3)]
    (fn []
      (if (< 0 @redirect-in)
        (js/setTimeout #(swap! redirect-in dec) 1000)
        (secretary/dispatch! (session.password/sign-in-path)))
      [:div.alert.alert-success
       [:h2  "The initial administrator user has been created"]
       [:p "You can sign in with the new account now. We will redirect you in " @redirect-in " seconds."]])))

(defn response-component []
  (when-let [response (-> @data :response presence)]
    (if @response-success?
      [response-success-component]
      [:div.alert.alert-danger
       [:h2  "Creating an administrator user failed"]
       (pre-component (select-keys response [:status :body :error-code :error-text]))
       ])))


;; form

(def show-form?
  (reaction
    (boolean (or (and (not @response-pending?)
                      (not @response-success?))
                 (not @request?)))))

(defn update-form-data [fun]
  (swap! state/client-state
         (fn [cs]
           (assoc-in cs [:create-admin :form-data]
                     (fun (-> cs :create-admin :form-data))))))

(defn update-form-data-value [k v]
  (update-form-data (fn [fd] (assoc fd k v))))

(def login-valid?
  (reaction
    (boolean
      (when-let [login (-> @form-data :login)]
        (re-matches #"^\w+$" login)))))

(def password-valid?
  (reaction
    (boolean
      (when-let [password (-> @form-data :password)]
        (not (re-matches #"^\s*$" password))))))

(def form-valid?
  (reaction (and @login-valid?
                 @password-valid?)))

(defn form []
  [:div.form
   [:div.form-group {:class (if-not @login-valid? "has-error" "")}
    [:label {:for "login"} "Login"]
    [:input {:type "text", :class "form-control", :id "login",
             :placeholder "Login (alphanumeric)" :value (:login @form-data)
             :on-change #(update-form-data-value
                           :login (-> % .-target .-value presence))}]]
   [:div.form-group {:class (if-not @password-valid? "has-error" "")}
    [:label {:for "password"} "Password"]
    [:input {:type "password", :class "form-control", :id "password",
             :placeholder "Password (may not be empty)"
             :value (:password @form-data)
             :on-change #(update-form-data-value
                           :password (-> % .-target .-value presence))}]]
   [:button.pull-right
    (merge {:type "submit", :class "btn btn-primary btn-default"
            :on-click post-create-admin}
           (when (not @form-valid?) {:disabled "yes"}))
    "Submit"][:div.clearfix]])

;; page

(defn debug-component []
  (when (:debug @state/client-state)
    [:section.debug
     [:h2 "Page Debug"]
     [:h3 "Data"]
     [pre-component @data]
     [:h3 "Form Data"]
     [pre-component @form-data]
     [:h3 "show-form?"]
     [pre-component @show-form?]
     [:h3 "request?"]
     [pre-component @request?]
     [:h3 "Response Pending"]
     [pre-component @response-pending?]]))

(defn page []
  [:div.create-initial-admin
   [:h1.text-danger "Create an initial administrator user"]
   [:p.text-warning
    " We recommend always to create an administrator user for password sign-in. "
    " Even if you use third party sing-in strategies." ]
   (response-component)
   (when @response-pending?
     [:div.jumbotron.text-center
      [:i.fa.fa-spinner.fa-spin.fa-3x.fa-fw]])
   (when @show-form? (form))
   (debug-component)])


