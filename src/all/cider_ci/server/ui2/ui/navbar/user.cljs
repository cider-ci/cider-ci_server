(ns cider-ci.server.ui2.ui.navbar.user
  (:require
    [cider-ci.server.ui2.constants :refer [CONTEXT]]

    [cider-ci.utils.core :refer [presence]]
    ))

(defn admin-actions [user]
  (when (:is_admin @user)
    [:li.dropdown.api-frontpage-actions
     [:a.dropdown-toggle
      {:href "/cider-ci/ui/admin/index",
       :data-toggle "dropdown"}
      [:i.fa.fa-cog]
      " Administration "
      [:b.caret]]
     [:ul.dropdown-menu
      [:li
       [:a {:href "/cider-ci/ui/admin/executors"}
        [:i.fa.fa-fw.fa-cogs]
        " Executors "]]
      [:li
       [:a {:href "/cider-ci/ui/admin/users"}
        [:i.fa.fa-fw.fa-users]
        " Users "]]
      [:li.divider]
      [:li
       [:a
        {:href "/cider-ci/ui/admin/status "}
        [:i.fa.fa-fw.fa-dashboard]
        " Users "]]
      [:li.divider]
      [:li
       [:a
        {:href "/cider-ci/ui/admin/welcome_page_settings/edit"}
        [:i.fa.fa-fw.fa-edit]
        " Welcome page settings "]]]]))

;(-> @state/client-state :current-url)

(defn sign-out-form [url]
  [:form
   {:action (str "/cider-ci/ui2/session/sign-out")
    :method :post}
   [:input {:type "hidden"
            :name :url
            :value @url}]
   [:button.btn.btn-warning.nabar-btn {:type "submit"}
    [:span " Sign out! "
     [:i.fa.fa-sign-out]]]])

(defn user-nav [user url]
  (when @user
    [:li
     [:a.dropdown-toggle
      {:data-toggle "dropdown"
       :href "#"}
      (if (:is_admin @user)
        [:i.fa.fa-user-md]
        [:i.fa.fa-user])
      " " (:login @user) " "
      [:b.caret]]
     [:ul.dropdown-menu
      [:li (sign-out-form url)]
      [:li
       [:a {:href "/cider-ci/ui/workspace/account/edit"}
        [:i.fw.fa.fa-pencil]
        " Account " ]]
      [:li.divider]
      [:li [:a {:href (str "/cider-ci/ui2/debug")} " Debug Page "]]]]))


(defn li [user current-url] (user-nav user current-url))
