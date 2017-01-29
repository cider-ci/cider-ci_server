(ns cider-ci.ui2.welcome-page.ui
  (:refer-clojure :exclude [str keyword])
  (:require-macros
    [reagent.ratom :as ratom :refer [reaction]]
    [cljs.core.async.macros :refer [go]])
  (:require
    [cider-ci.ui2.shared :refer [pre-component anti-forgery-token]]
    [cider-ci.utils.core :refer [keyword str presence deep-merge]]
    [cider-ci.ui2.constants :refer [CONTEXT]]
    [cider-ci.client.state :as state]
    [cider-ci.ui2.session.password.ui :as session.password]

    [cider-ci.utils.markdown :as markdown]
    [reagent.core :as reagent]
    [secretary.core :as secretary :include-macros true]
    [cljs-http.client :as http]
    ))

(declare page get-welcome-page-settings)

(secretary/defroute create-admin-path (str CONTEXT "/welcome-page/edit") []
  (swap! state/page-state assoc :current-page
         {:component #'page})
  (swap! state/client-state assoc
         :welcome-page-settings {:data {:form {}}
                                 :submit {}})
  (get-welcome-page-settings))

(def form-data (reaction (-> @state/client-state :welcome-page-settings :data :form)))

(def submit-status
  (reaction
    (cond
      (not (-> @state/client-state :welcome-page-settings
               :submit :request presence boolean)) :none
      (and (-> @state/client-state :welcome-page-settings
               :submit :request presence boolean)
           (not (-> @state/client-state :welcome-page-settings
                    :submit :response presence boolean))) :pending
      (-> @state/client-state
          :welcome-page-settings
          :submit :response
          presence boolean)  (if  (-> @state/client-state
                                    :welcome-page-settings
                                      :submit :response :success)
                               :success :failed))))

(def get-welcome-page-settings-success?
  (reaction (-> @state/client-state
                :welcome-page-settings :get :resp :success boolean)))

(def welcome-page-settings
  (reaction (-> @state/client-state :welcome-page-settings)))

(defn get-welcome-page-settings []
  (let [request {:url (str CONTEXT "/welcome-page-settings")
                 :method :get
                 :headers {"accept" "application/json"
                           }}]
    (go (let [resp (<! (http/request request))]
          (swap! state/client-state
                 assoc-in [:welcome-page-settings :get :resp]
                 (clojure.walk/keywordize-keys resp))
          (if @get-welcome-page-settings-success?
            (swap! state/client-state
                   assoc-in [:welcome-page-settings :data :form :welcome_message]
                   (or (-> resp :body :welcome_message presence)
                       "")))))))

(defn submit-form []
  (let [request {:url (str CONTEXT "/welcome-page-settings")
                 :headers {"accept" "application/json"
                           "X-CSRF-Token" (anti-forgery-token)}
                 :method :patch
                 :json-params (select-keys @form-data [:welcome_message])}]
    (swap! state/client-state
           assoc-in [:welcome-page-settings :submit :request] request)
    (swap! state/client-state
           update-in [:welcome-page-settings :submit] #(dissoc % :response))
    (go (let [resp (<! (http/request request))]
          (swap! state/client-state
                 assoc-in [:welcome-page-settings :submit :response]
                 (clojure.walk/keywordize-keys resp))))))

(defn debug []
  (when (:debug @state/client-state)
    [:section#local-debug
     [:hr]
     [:h2 "Local Debug"]
     [pre-component
      {:form-data @form-data
       :get-welcome-page-settings-success?
       @get-welcome-page-settings-success?
       :submit-status @submit-status
       }]]))

(defn response-success-component []
  (let [redirect-in (reagent/atom 3)]
    (fn []
      (if (< 0 @redirect-in)
        (js/setTimeout #(swap! redirect-in dec) 1000)
        (set! (.-href js/location) (str CONTEXT "/")))
      [:div.alert.alert-success
       [:h2  "Welcome Message Saved"]
       [:p " We will redirect you in " @redirect-in " seconds."]])))

(defn response-failed-component []
  (when-let [response (-> @state/client-state :welcome-page-settings :submit :response presence)]
    [:div.alert.alert-danger
     [:h2  "Submit Failed"]
     (pre-component (select-keys response [:status :body :error-code :error-text]))
     ]))

(defn response-component []
  [:div#submit-response
   (case @submit-status
     :success [response-success-component]
     :failed [response-failed-component]
     [:div])])

(defn form-welcome-message-edit []
  [:div.form-group.welcome-message
   [:label "Welcome Message ("
    [:a {:href "https://en.wikipedia.org/wiki/Markdown#Example"}
     "markdown" ] ")"]
   [:textarea.form-control
    {:value (-> @state/client-state :welcome-page-settings :data :form :welcome_message)
     :rows 7
     :on-change #(swap! state/client-state
                        assoc-in [:welcome-page-settings :data :form :welcome_message]
                        (-> % .-target .-value presence))}]])

(defn form-welcome-message-preview []
  [:div.form-group.welcome-message-preview
   [:label "Welcome Message Preview"]
   [:div
    {:style {:background-color "lightgray" :padding "1em"}
     :dangerouslySetInnerHTML
     {:__html (markdown/md2html
                (or (-> @state/client-state :welcome-page-settings
                        :data :form :welcome_message presence) ""))}}]])

(defn form-welcome-message []
  [:div.row
   [:div.col-md-6
    [form-welcome-message-edit]]
   [:div.col-md-6
    [form-welcome-message-preview]]])

(defn form-submit []
  [:button.pull-right
   (merge {:type "submit", :class "btn btn-primary btn-default"
           :on-click submit-form})
   "Submit"])

(defn form []
  (when-not (= :success @submit-status)
    [:div.form
     [form-welcome-message]
     [form-submit]
     [:div.clearfix]]))

(defn page []
  [:div
   [:h1 "Edit Welcome Page Settings"]
   (if-not @get-welcome-page-settings-success?
     [:div.jumbotron.text-center [:i.fa.fa-spinner.fa-spin.fa-3x.fa-fw]]
     [form])
   [response-component]
   [debug]])
