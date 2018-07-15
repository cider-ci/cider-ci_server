(ns cider-ci.server.front.breadcrumbs
  (:require
    [cider-ci.server.front.icons :as icons]
    [cider-ci.server.paths :as paths :refer [path]]
    [cider-ci.server.front.state :as state]))

(defn li
  [handler-key inner & [route-params query-params]]
  (let [active? (= (-> @state/routing-state* :handler-key) handler-key)]
    [:li.breadcrumb-item {:key handler-key :class (if active? "active" "")}
     (if active?
       [:span inner]
       [:a {:href (path handler-key route-params query-params)} inner])]))


(defn admin-li [] (li :admin [:span icons/admin  " Admin "]))
(defn auth-li [] (li :auth "Authentication"))
(defn auth-password-sign-in-li [] (li :auth-password-sign-in "Password sign-in"))
(defn debug-li [] (li :debug "Debug"))

(defn initial-admin-li [] (li :initial-admin "Initial-Admin"))
(defn home-li [] (li :home [:span icons/home " Home "]))

(defn api-token-li [user-id api-token-id]
  (li :api-token [:span " API-Token "] {:user-id user-id :api-token-id api-token-id} {}))
(defn api-tokens-li [id]
  (li :api-tokens [:span " API-Tokens "] {:user-id id} {}))
(defn api-token-new-li [id]
  (when (= id (:id @state/user*))
    (li :api-token-new [:span [:i.fas.fa-plus-circle] " Add API-Token "] {:user-id id} {})))
(defn api-token-edit-li [user-id api-token-id]
  (when (= user-id (:id @state/user*))
    (li :api-token-edit [:span [:i.fas.fa-edit] " Edit API-Token "]
        {:user-id user-id :api-token-id api-token-id} {})))
(defn api-token-delete-li [user-id api-token-id]
  (when (= user-id (:id @state/user*))
    (li :api-token-delete [:span [:i.fas.fa-times] " Delete API-Token "]
        {:user-id user-id :api-token-id api-token-id} {})))

(defn commits-li [] (li :commits [:span icons/commits " Commits "]))

(defn email-addresses-add-li [id]
  (when (= id (:id @state/user*))
    (li :email-addresses-add [:span icons/add " Add email address "] {:user-id id} {})))
(defn email-addresses-li [user-id] 
  (li :email-addresses [:span icons/email-addresses " Email addresses"] {:user-id user-id} {}))

(defn gpg-key-li [user-id gpg-key-id]
  (li :gpg-keys-add 
      [:span icons/gpg-key " GPG key"] 
      {:user-id user-id :gpg-key-id gpg-key-id} {}))
(defn gpg-key-edit-li [user-id gpg-key-id]
  (li :gpg-key-edit
      [:span icons/edit " Edit "] 
      {:user-id user-id :gpg-key-id gpg-key-id} {}))
(defn gpg-keys-add-li [id]
    (li :gpg-keys-add [:span icons/add " Add GPG key"] {:user-id id} {}))
(defn gpg-keys-li [id]
  (li :gpg-keys [:span icons/gpg-keys " GPG keys "] {:user-id id} {}))


(defn project-li [project-id] 
  (li :project [:span icons/project " Project "] {:project-id project-id}))
(defn project-delete-li [project-id] 
  (li :project-delete [:span icons/delete " Delete "] {:project-id project-id}))
(defn project-edit-li [project-id] 
  (li :project-edit [:span icons/edit " Edit "] {:project-id project-id}))
(defn projects-li [] (li :projects [:span icons/projects " Projects "]))
(defn projects-add-li [] (li :projects-add [:span icons/add" Add project "]))

(defn request-li [id] (li :request "Request" {:id id} {}))
(defn requests-li [] (li :requests "Requests"))

(defn user-delete-li [id] (li :user-delete [:span [:i.fas.fa-times] " Delete "] {:user-id id} {}))
(defn user-edit-li [id] (li :user-edit [:span [:i.fas.fa-edit] " Edit "] {:user-id id} {}))
(defn user-li [id] (li :user [:span icons/user " User "] {:user-id id} {}))
(defn user-new-li [] (li :user-new [:span icons/add " New user "]))
(defn users-li [] (li :users [:span icons/users " Users "] {} (:users-query-params @state/global-state*)))

(defn nav-component [left right]
  [:div.row.nav-component.mt-3 
   [:nav.col-lg {:aria-label :breadcrumb :role :navigation}
    (when (seq left)
      [:ol.breadcrumb
       (for [li left] li) ])]
   [:nav.col-lg {:role :navigation}
    (when (seq right)
      [:ol.breadcrumb.nav-right
       (for [li right] li)])]])
