(ns cider-ci.server.resources.users.front
  (:refer-clojure :exclude [str keyword])
  (:require-macros
    [reagent.ratom :as ratom :refer [reaction]]
    [cljs.core.async.macros :refer [go]])
  (:require
    [cider-ci.server.front.icons :as icons]
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

(def current-query-paramerters* (reaction (-> @state/routing-state* :query-params)))

(def default-query-parameters {:is_admin nil
                               :page 1
                               :per-page 12
                               :term "" })

(def current-query-paramerters-normalized*
  (reaction (merge default-query-parameters
           @current-query-paramerters*)))

(def fetch-users-id* (reagent/atom nil))
(def users* (reagent/atom {}))
(def page-is-active?* (reaction (= (-> @state/routing-state* :handler-key) :users)))

(defn fetch-users []
  (let [query-paramerters @current-query-paramerters-normalized*]
    (go (<! (timeout 200))
        (when (= query-paramerters @current-query-paramerters-normalized*)
          (let [resp-chan (async/chan)
                id (requests/send-off {:url (path :users) :method :get
                                       :query-params query-paramerters}
                                      {:modal false
                                       :title "Fetch Users"
                                       :handler-key :users
                                       :retry-fn #'fetch-users}
                                      :chan resp-chan)]
            (reset! fetch-users-id* id)
            (go (let [resp (<! resp-chan)]
                  (when (and (= (:status resp) 200) ;success
                             (= id @fetch-users-id*) ;still the most recent request
                             (= query-paramerters @current-query-paramerters-normalized*)) ;query-params have not changed yet
                    ;(reset! effective-query-paramerters* current-query-paramerters)
                    (swap! users* assoc query-paramerters (->> (-> resp :body :users)
                                                               (map-indexed (fn [idx u]
                                                                              (assoc u :key (:id u)
                                                                                     :c idx)))
                                                               ))))))))))

(defn escalate-query-paramas-update [_]
  (fetch-users)
  (swap! state/global-state*
         assoc :users-query-params @current-query-paramerters-normalized*))

(defn current-query-params-component []
  (reagent/create-class
    {:component-did-mount escalate-query-paramas-update
     :component-did-update escalate-query-paramas-update
     :reagent-render
     (fn [_] [:div.current-query-parameters
              {:style {:display (if @state/debug?* :block :none)}}
              [:h3 "@current-query-paramerters-normalized*"]
              [components/pre-component @current-query-paramerters-normalized*]])}))


;;; helpers ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn page-path-for-query-params [query-params]
  (path (:handler-key @state/routing-state*) 
        (:route-params @state/routing-state*)
        (merge @current-query-paramerters-normalized*
               query-params)))


;;; Filter ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn form-term-filter []
  [:div.form-group.ml-2.mr-2.mt-2
   [:label.sr-only {:for :users-search-term} "Search term"]
   [:input#users-search-term.form-control.mb-1.mr-sm-1.mb-sm-0
    {:type :text
     :placeholder "Search term ..."
     :value (or (-> @current-query-paramerters-normalized* :term presence) "")
     :on-change (fn [e]
                  (let [val (or (-> e .-target .-value presence) "")]
                    (accountant/navigate! (page-path-for-query-params
                                            {:page 1 :term val}))))}]])

(defn form-admins-filter []
  [:div.form-group.ml-2.mr-2.mt-2
   [:label
    [:span.pr-1 "Admins only"]
    [:input
     {:type :checkbox
      :on-change #(let [new-state (case (-> @current-query-paramerters-normalized*
                                            :is_admin presence)
                                    ("true" true) nil
                                    true)]
                    (js/console.log (with-out-str (pprint new-state)))
                    (accountant/navigate! (page-path-for-query-params 
                                             {:page 1
                                              :is_admin new-state})))
      :checked (case (-> @current-query-paramerters-normalized*
                         :is_admin presence)
                 (nil false "false") false
                 ("true" true) true)}]]])


(defn form-per-page []
  (let [per-page (or (-> @current-query-paramerters-normalized* :per-page presence) "12")]
    [:div.form-group.ml-2.mr-2.mt-2
     [:label.mr-1 {:for :users-filter-per-page} "Per page"]
     [:select#users-filter-per-page.form-control
      {:value per-page
       :on-change (fn [e]
                    (let [val (or (-> e .-target .-value presence) "12")]
                      (accountant/navigate! (page-path-for-query-params
                                              {:page 1
                                               :per-page val}))))}
      (for [p [12 25 50 100 250 500 1000]]
        [:option {:key p :value p} p])]]))

(defn form-reset []
  [:div.form-group.mt-2
   [:label.sr-only {:for :users-filter-reset} "Reset"]
   [:a#users-filter-reset.btn.btn-warning
    {:href (page-path-for-query-params default-query-parameters)}
    [:i.fas.fa-times]
    " Reset "]])

(defn filter-form []
  [:div.card.bg-light
   [:div.card-body
   [:div.form-inline
    [form-term-filter]
    [form-admins-filter]
    [form-per-page]
    [form-reset]]]])

;;; Table ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def default-colconfig 
  {:id true
   :org_id true
   :firstname true
   :lastname true
   :email true
   :customcols []})

(defn users-thead-component [colconfig]
  [:thead
   [:tr
    [:th]
    [:th]
    (when (:id colconfig) [:th "Id"])
    (when (:org_id colconfig) [:th "Org id"])
    (when (:firstname colconfig) [:th "Firstname"])
    (when (:lastname colconfig) [:th "Lastname"]) 
    (when (:email colconfig) [:th "Email"])
    (for [[hc _] (:customcols colconfig)]
      [hc])]])

(defn user-row-component [colconfig user]
  [:tr {:key (:key user)}
   [:td (:count user)]
   [:td [:a {:href (path :user {:user-id (:id user)})}
         [:img {:src (or (:img32_data_url user)
                         (gravatar-url (:primary_email_address user)))}]]]
   (when (:id colconfig)
     [:td [:a {:href (path :user {:user-id (:id user)})}
           (short-id (:id user))]])
   (when (:org_id colconfig)
     [:td {:style {:font-family "monospace"}} (:org_id user)])
   (when (:firstname colconfig)
     [:td (:firstname user)])
   (when (:lastname colconfig)
     [:td (:lastname user)])
   (when (:email colconfig)
     [:td [:a {:href (str "mailto:" (:primary_email_address user))}
           [:i.fas.fa-envelope] " " (:primary_email_address user)]])
   (for [[_ tdc] (:customcols colconfig)]
     [tdc user])])

(defn users-table-component [colconfig]
  (if-not (contains? @users* @current-query-paramerters-normalized*)
    [:div.text-center
     [:i.fas.fa-spinner.fa-spin.fa-5x]
     [:span.sr-only "Please wait"]]
    (if-let [users (-> @users* (get  @current-query-paramerters-normalized* []) seq)]
      [:table.table.table-striped.table-sm
       [users-thead-component colconfig]
       [:tbody
        (let [page (:page @current-query-paramerters-normalized*)
              per-page (:per-page @current-query-paramerters-normalized*)]
          (doall (for [user users]
                   (user-row-component colconfig
                                       (assoc user :count (+ 1 (:c user) (* per-page (- page 1))))))))]]
      [:div.alert.alert-warning.text-center "No (more) users found."])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn pagination-component []
  [:div.clearfix.mt-2.mb-2
   (let [page (dec (:page @current-query-paramerters-normalized*))]
     [:div.float-left
      [:a.btn.btn-primary.btn-sm
       {:class (when (< page 1) "disabled")
        :href (page-path-for-query-params {:page page})}
       [:i.fas.fa-arrow-circle-left] " Previous " ]])
   [:div.float-right
    [:a.btn.btn-primary.btn-sm
     {:href (page-path-for-query-params 
              {:page (inc (:page @current-query-paramerters-normalized*))})}
     " Next " [:i.fas.fa-arrow-circle-right]]]])

(defn debug-component []
  (when (:debug @state/global-state*)
    [:section.debug
     [:hr]
     [:h2 "Page Debug"]
     [:div.users
      [:h3 "@users*"]
      [:pre (with-out-str (pprint @users*))]]]))

(defn main-page-content-component [colconfig]
  [:div
   [current-query-params-component]
   [filter-form]
   [pagination-component]
   [users-table-component colconfig]
   [pagination-component]
   [debug-component]])

(defn page []
  [:div.users
   (breadcrumbs/nav-component
     [(breadcrumbs/home-li)
      (breadcrumbs/admin-li)
      (breadcrumbs/users-li)]
     [(breadcrumbs/user-new-li)])
   [:h1 icons/users " Users "]
   [main-page-content-component default-colconfig]])
