(ns cider-ci.server.resources.gpg-keys.front
  (:refer-clojure :exclude [str keyword])
  (:require-macros
    [reagent.ratom :as ratom :refer [reaction]]
    [cljs.core.async.macros :refer [go]])
  (:require
    [cider-ci.server.front.breadcrumbs :as breadcrumbs]
    [cider-ci.server.front.icons :as icons]
    [cider-ci.server.front.requests.core :as requests]
    [cider-ci.server.front.shared :as shared :refer [humanize-datetime-component]]
    [cider-ci.server.front.state :as state :refer [routing-state*]]
    [cider-ci.server.paths :as paths :refer [path]]
    [cider-ci.server.resources.user.front :as user]

    [cider-ci.utils.url :as url]
    [cider-ci.utils.seq :refer [with-index]]

    [cider-ci.utils.core :refer [keyword str presence]]

    [accountant.core :as accountant]
    [cljs.pprint :refer [pprint]]
    [cljs.core.async :as async]
    [cljs.core.async :refer [timeout]]
    [reagent.core :as reagent]))


(defonce gpg-key-id* (reaction (-> @state/routing-state* :route-params :gpg-key-id)))
(defonce user-id* (reaction (-> @state/routing-state* :route-params :user-id)))
(defonce gpg-key-data* (reagent/atom nil))
(defonce gpg-keys-data* (reagent/atom nil)) 


(declare fetch-gpg-keys)


;### add ######################################################################

(defn post-add []
  (let [resp-chan (async/chan)
        id (requests/send-off {:url (path :gpg-keys-add {:user-id @user-id*})
                               :method :post
                               :json-params  @gpg-key-data*}
                              {:modal true
                               :title "Add GPG key"
                               :retry-fn post-add}
                              :chan resp-chan)]
    (go (let [resp (<! resp-chan)]
          (when (#{201 204} (:status resp))
            (accountant/navigate! (path :gpg-keys {:user-id @user-id*})))))))

(defn submit-add-gpg-key [e]
  (.preventDefault e)
  (post-add))

(defn add-form-component []
  [:form.form
   {:on-submit submit-add-gpg-key}
   [:div.form-group
    [:label  {:for :description} "Description"]
    [:textarea#description.form-control
     {:value (-> @gpg-key-data* :description)
      :on-change #(swap! gpg-key-data* assoc :description (-> % .-target .-value))}]]
   [:div.form-group
    [:label  {:for :key} "Key"]
    [:textarea#key.form-control
     {:value (-> @gpg-key-data* :key)
      :on-change #(swap! gpg-key-data* assoc :key (-> % .-target .-value))}]]
   [:button.btn.btn-primary.float-right
    {:type :submit}
    "Add GPG key"]
   [:div.clearfix]])

(defn refresh-add-data [& args]
  (user/clean-and-fetch nil)
  (reset! gpg-key-data* {}))

(defn add-page []
  [:div
   [state/hidden-routing-state-component
    {:will-mount refresh-add-data 
     :did-change refresh-add-data}]
   (breadcrumbs/nav-component
     [(breadcrumbs/home-li)
      (breadcrumbs/admin-li)
      (breadcrumbs/users-li)
      (breadcrumbs/user-li @user-id*)
      (breadcrumbs/gpg-keys-li @user-id*)
      (breadcrumbs/gpg-keys-add-li @user-id*)]
     [])
   [:div
    [:h1 "Add GPG for " [user/name-component]]
    [add-form-component]]])


;### delete ###################################################################

(defn delete [& args]
  (let [resp-chan (async/chan)
        id (requests/send-off {:url (path :gpg-key {:user-id @user-id*
                                                    :gpg-key-id @gpg-key-id*})
                               :method :delete}
                              {:modal true
                               :title "Delete GPG key"
                               :retry-fn delete}
                              :chan resp-chan)]
    (go (let [resp (<! resp-chan)]
          (when (#{201 204} (:status resp))
            (accountant/navigate! (path :gpg-keys {:user-id @user-id*})))))))

(defn delete-button []
  [:button.btn.btn-sm.btn-danger
   {:on-click delete}
   icons/delete " Delete " ])



;### show page ################################################################

(def fetch-gpg-key-id* (reagent/atom nil))
(defn fetch-gpg-key [& args]
  (let [resp-chan (async/chan)
        id (requests/send-off {:url (path :gpg-key {:user-id @user-id*
                                                    :gpg-key-id @gpg-key-id*})
                               :method :get}
                              {:modal false
                               :title "Fetch gpg-key"
                               :retry-fn #'fetch-gpg-key}
                              :chan resp-chan)]
    (reset! fetch-gpg-key-id* id)
    (go (let [resp (<! resp-chan)]
          (when (and (= (:status resp) 200) ;success
                     (= id @fetch-gpg-key-id*) ;still the most recent request
                     (reset! gpg-key-data* (->> resp :body))))))))

(defn refresh-show-data [& args]
  (user/clean-and-fetch nil)
  (reset! gpg-key-data* nil)
  (fetch-gpg-key))

(defn show-page []
  [:div
   [state/hidden-routing-state-component
    {:will-mount refresh-show-data 
     :did-change refresh-show-data}]
   (breadcrumbs/nav-component
     [(breadcrumbs/home-li)
      (breadcrumbs/admin-li)
      (breadcrumbs/users-li)
      (breadcrumbs/user-li @user-id*)
      (breadcrumbs/gpg-keys-li @user-id*)
      (breadcrumbs/gpg-key-li @user-id* @gpg-key-id*)]
     [[:li.breadcrumb-item {:key :delete} (delete-button)]
      (breadcrumbs/gpg-key-edit-li @user-id* @gpg-key-id*)])
   [:div
    [:h1 "Public GPG key " " of " [user/name-component]]
    (if-let [gpg-key-data @gpg-key-data*]
      [:div.gpg-key
       [:div.added-at
        [:p "This key has been added " 
         (humanize-datetime-component (:created_at gpg-key-data)) "."]]
       [:div
        [:h3 "Fingerprints"]
        [:div
         (for [[idx fingerprint] (map-indexed 
                                   (fn [idx x] [idx x])
                                   (:fingerprints gpg-key-data))]
           [:span {:key idx}
            [:span.badge.badge-secondary.mx-1
             fingerprint] " "])]]
       [:div.key
        [:h3 "Description "]
        [:pre.gpg-key-key.bg-light
         (:description gpg-key-data)]]
       [:div.key
        [:h3 "Key "]
        [:pre.gpg-key-key.bg-light
         (:key gpg-key-data)]]]
      [shared/please-wait-component])]])


;### edit #####################################################################

(defn patch []
  (let [resp-chan (async/chan)
        id (requests/send-off {:url (path :gpg-key {:user-id @user-id*
                                                    :gpg-key-id @gpg-key-id*})
                               :method :patch
                               :json-params (select-keys @gpg-key-data* [:description])}
                              {:modal true
                               :title "Update GPG key"
                               :retry-fn patch}
                              :chan resp-chan)]
    (go (let [resp (<! resp-chan)]
          (when (#{201 204} (:status resp))
            (accountant/navigate! (path :gpg-key {:user-id @user-id*
                                                  :gpg-key-id @gpg-key-id*})))))))

(defn submit-patch-gpg-key [e]
  (.preventDefault e)
  (patch))

(defn edit-form-component []
  [:form.form
   {:on-submit submit-patch-gpg-key}
   [:div.form-group
    [:label  {:for :description} "Description"]
    [:textarea#description.form-control
     {:value (-> @gpg-key-data* :description)
      :on-change #(swap! gpg-key-data* assoc :description (-> % .-target .-value))}]]
   [:button.btn.btn-primary.float-right
    {:type :submit}
    "Save"]
   [:div.clearfix]])

(defn refresh-edit-data [& args]
  (user/clean-and-fetch nil)
  (reset! gpg-key-data* nil)
  (fetch-gpg-key))

(defn edit-page []
  [:div
   [state/hidden-routing-state-component
    {:will-mount refresh-edit-data
     :did-change refresh-edit-data}]
   (breadcrumbs/nav-component
     [(breadcrumbs/home-li)
      (breadcrumbs/admin-li)
      (breadcrumbs/users-li)
      (breadcrumbs/user-li @user-id*)
      (breadcrumbs/gpg-keys-li @user-id*)
      (breadcrumbs/gpg-keys-add-li @user-id*)]
     [])
   [:div
    [:h1 "Edit public GPG key " " of " [user/name-component]]
    (if-not @gpg-key-data*
      [shared/please-wait-component]
      [:div
       [:p "Changing the key itself is not possible. " 
        "The proper worklfow would be to delete this key and create a new GPG key. " ]
       [edit-form-component]])]])


;### index ####################################################################

(def fetch-gpg-keys-id* (reagent/atom nil))
(defn fetch-gpg-keys [& args]
  (let [resp-chan (async/chan)
        id (requests/send-off {:url (path :gpg-keys {:user-id @user-id*})
                               :method :get}
                              {:modal false
                               :title "Fetch gpg-keys"
                               :retry-fn #'fetch-gpg-keys}
                              :chan resp-chan)]
    (reset! fetch-gpg-keys-id* id)
    (go (let [resp (<! resp-chan)]
          (when (and (= (:status resp) 200) ;success
                     (= id @fetch-gpg-keys-id*) ;still the most recent request
                     (reset! gpg-keys-data* 
                             (update-in (->> resp :body) [:gpg_keys] 
                                        (partial with-index 0)))))))))
           

(defn index-table-component []
  (if-let [gpg-keys-data (:gpg_keys @gpg-keys-data*)]
    [:table.table.table-striped.table-sm
     [:thead
      [:tr 
       [:th]
       [:th "Description"]
       [:th "Fingerprints"]
       [:th "Added"]]]
     [:tbody
      (doall (for [gpg-key-data gpg-keys-data]
               [:tr {:key (:id gpg-key-data)}
                [:td {:key :index} 
                 [:a {:href 
                      (path :gpg-key {:user-id @user-id* 
                                      :gpg-key-id (:id gpg-key-data)})}
                  (:index gpg-key-data)]]
                [:td 
                 {:key :description
                  :style {:white-space :nowrap
                          :overflow :hidden
                          :max-width :10ch
                          :text-overflow :ellipsis}}
                 [:a {:href (path :gpg-key {:user-id @user-id* 
                                            :gpg-key-id (:id gpg-key-data)})} 
                  (:description gpg-key-data)]]
                [:td {:key :fingerprints}
                 (for [[idx fingerprint] (map-indexed 
                                         (fn [idx x] [idx x])
                                         (:fingerprints gpg-key-data))]
                   [:span.badge.badge-secondary.mx-1
                    {:key idx}
                    (->> (clojure.string/split fingerprint "")
                         (take 8)
                         (clojure.string/join ""))])]
                [:td {:key :added_at}
                 (-> gpg-key-data :created_at humanize-datetime-component)]]))]]
    [shared/please-wait-component]))

(defn index-debug-component []
  (when (:debug @state/global-state*)
    [:section.debug
     [:hr]
     [:h2 "Page Debug"]
     [:div.users
      [:h3 "@gpg-keys-data*"]
      [:pre (with-out-str (pprint @gpg-keys-data*))]]]))

(defn refresh-index-data [& args]
  (user/clean-and-fetch nil)
  (fetch-gpg-keys))

(defn index-page []
  [:div
   [state/hidden-routing-state-component
    {:will-mount refresh-index-data
     :did-change refresh-index-data}]
   (breadcrumbs/nav-component
     [(breadcrumbs/home-li)
      (breadcrumbs/admin-li)
      (breadcrumbs/users-li)
      (breadcrumbs/user-li @user-id*)
      (breadcrumbs/gpg-keys-li @user-id*)]
     [(breadcrumbs/gpg-keys-add-li @user-id*)])
   [:div
    [:h1 "Public GPG keys for " [user/name-component]]
    [index-table-component]
    [index-debug-component]]])
