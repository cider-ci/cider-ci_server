(ns cider-ci.server.resources.email-addresses.front
  (:refer-clojure :exclude [str keyword])
  (:require-macros
    [reagent.ratom :as ratom :refer [reaction]]
    [cljs.core.async.macros :refer [go]])
  (:require
    [cider-ci.server.front.breadcrumbs :as breadcrumbs]
    [cider-ci.server.front.icons :as icons]
    [cider-ci.server.front.requests.core :as requests]
    [cider-ci.server.front.shared :refer [humanize-datetime-component]]
    [cider-ci.server.front.state :as state :refer [routing-state*]]
    [cider-ci.server.paths :as paths :refer [path]]
    [cider-ci.server.resources.user.front :as user]

    [cider-ci.utils.url :as url]
    [cider-ci.utils.core :refer [keyword str presence]]

    [accountant.core :as accountant]
    [cljs.pprint :refer [pprint]]
    [cljs.core.async :as async]
    [cljs.core.async :refer [timeout]]
    [reagent.core :as reagent]

    ))


(defonce user-id* (reaction (-> @state/routing-state* :route-params :user-id)))
(defonce email-address* (reaction (-> @state/routing-state* :route-params :email-address)))
(defonce email-address-data* (reagent/atom nil))
(defonce email-addresses-data* (reagent/atom nil)) 

(declare fetch-email-addresses)

(defn refresh-data [& args]
  (reset! email-addresses-data* nil)
  (reset! email-address-data* {:email_address ""})
  (fetch-email-addresses)
  (user/clean-and-fetch nil))


;### add ######################################################################

(defn post-add []
  (let [resp-chan (async/chan)
        id (requests/send-off {:url (path :email-addresses-add {:user-id @user-id*})
                               :method :post
                               :json-params  @email-address-data*}
                              {:modal true
                               :title "Add email address"
                               :handler-key :email-addresses-add
                               :retry-fn post-add}
                              :chan resp-chan)]
    (go (let [resp (<! resp-chan)]
          (when (#{201 204} (:status resp))
            (accountant/navigate! (path :email-addresses {:user-id @user-id*})))))))

(defn submit-add-email-address [e]
  (.preventDefault e)
  (post-add))

(defn form-component []
  [:form.form
   {:on-submit submit-add-email-address}
   [:div.form-group
    [:label  {:for :email-address} "Email address"]
    [:input#email-address.form-control
     {:type :email
      :value (-> @email-address-data* :email_address)
      :on-change #(swap! email-address-data* assoc :email_address (-> % .-target .-value))}]
    [:small.form-text
     "Downcased email addresses are unique withing in an instance of Cider-CI, i.e. "
     "the same email address can not be added for two or more users. " ]]
   [:button.btn.btn-primary.float-right
    {:type :submit}
    "Add email address"]
   [:div.clearfix]])

(defn add-page []
  [:div
   [state/hidden-routing-state-component
    {:will-mount refresh-data 
     :did-change refresh-data}]
   (breadcrumbs/nav-component
     [(breadcrumbs/home-li)
      (breadcrumbs/admin-li)
      (breadcrumbs/users-li)
      (breadcrumbs/user-li @user-id*)
      (breadcrumbs/email-addresses-li @user-id*)
      (breadcrumbs/email-addresses-add-li @user-id*)]
     [])
   [:div
    [:h1 "Add email address for " [user/name-component]]
    [form-component]]])


;### index ####################################################################


(def fetch-email-addresses-id* (reagent/atom nil))
(defn fetch-email-addresses [& args]
  (let [resp-chan (async/chan)
        id (requests/send-off {:url (path :email-addresses {:user-id @user-id*})
                               :method :get}
                              {:modal false
                               :title "Fetch email-addresses"
                               :handler-key :email-addresses
                               :retry-fn #'fetch-email-addresses}
                              :chan resp-chan)]
    (reset! fetch-email-addresses-id* id)
    (go (let [resp (<! resp-chan)]
          (when (and (= (:status resp) 200) ;success
                     (= id @fetch-email-addresses-id*) ;still the most recent request
                     (reset! email-addresses-data* (->> resp :body :email_addresses
                                                   (sort-by :email_address)
                                                   (map-indexed #(assoc %2 
                                                                        :key (:email_address %2)
                                                                        :c (inc %1)))))))))))

(defn set-as-primary [email-address]
  (let [resp-chan (async/chan)
        id (requests/send-off {:url (path :email-address 
                                          {:user-id @user-id*
                                           :email-address (url/encode email-address)}) 
                               :method :post
                               :headers {"accept" "*/*"}}
                              {:modal true
                               :title "Set as primary"
                               :handler-key :email-address
                               :retry-fn (fn [] (set-as-primary email-address))}
                              :chan resp-chan)]
    (go (let [resp (<! resp-chan)]
          (when (#{201 204} (:status resp))
            (refresh-data))))))

(defn delete [email-address]
  (let [resp-chan (async/chan)
        id (requests/send-off {:url (path :email-address 
                                          {:user-id @user-id*
                                           :email-address (url/encode email-address)}) 
                               :method :delete
                               :headers {"accept" "*/*"}}
                              {:modal true
                               :title "Delete"
                               :handler-key :email-address
                               :retry-fn (fn [] (delete email-address))}
                              :chan resp-chan)]
    (go (let [resp (<! resp-chan)]
          (when (#{201 204} (:status resp))
            (refresh-data))))))

(defn index-table-component []
  (if (nil? @email-addresses-data*)
    [:div.text-center
     [:i.fas.fa-spinner.fa-spin.fa-5x]
     [:span.sr-only "Please wait"]]
    [:table.table.table-striped.table-sm
     [:tbody
      (doall (for [email-address-data @email-addresses-data*]
               (let [email-address (:email_address email-address-data)
                     is-primary? (= email-address (:primary_email_address @user/user-data*))]
                 [:tr {:key email-address}
                  [:td {:key :index} (:c email-address-data)]
                  [:td {:key :email-address} 
                   (if is-primary?
                     [:b email-address]
                     email-address)]
                  [:td {:key :added_at}
                   " added "
                   (-> email-address-data :created_at humanize-datetime-component)]
                  [:td {:key :actions}
                   (when (not is-primary?)
                     [:span
                      [:button.btn.btn-sm.btn-warning 
                       {:on-click #(delete email-address)}
                       [:span icons/delete " Delete "]]
                      " "
                      [:button.btn.btn-sm.btn-primary 
                       {:on-click #(set-as-primary email-address)}
                       " Set as primary "]])]])))]]))

(defn index-debug-component []
  (when (:debug @state/global-state*)
    [:section.debug
     [:hr]
     [:h2 "Page Debug"]
     [:div.users
      [:h3 "@email-addresses-data*"]
      [:pre (with-out-str (pprint @email-addresses-data*))]]]))

(defn index-page []
  [:div
   [state/hidden-routing-state-component
    {:will-mount refresh-data 
     :did-change refresh-data}]
   (breadcrumbs/nav-component
     [(breadcrumbs/home-li)
      (breadcrumbs/admin-li)
      (breadcrumbs/users-li)
      (breadcrumbs/user-li @user-id*)
      (breadcrumbs/email-addresses-li @user-id*)]
     [(breadcrumbs/email-addresses-add-li @user-id*)]) 
   [:div
    [:h1 "Email addresses for " [user/name-component]]
    [index-table-component]
    [index-debug-component]]])
