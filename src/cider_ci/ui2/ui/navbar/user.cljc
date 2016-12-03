(ns cider-ci.ui2.ui.navbar.user
  (:require
    [cider-ci.utils.core :refer [presence]]
    #?(:clj [logbug.debug :as debug])
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

(defn sign-in-nav [current-url current-path]
  [:div.navbar-form
   (when-not (= (str "/cider-ci/ui2/session/password-sign-in")
                @current-path)
     [:a.btn.btn-info
      {:href (str "/cider-ci/ui2/session/password-sign-in?url="
                  #?(:cljs (js/encodeURIComponent @current-url))
                  #?(:clj "TODO"))}
      [:i.fa.fa-sign-in.fa-fw]
      " Sign in with password"
      ])])

(defn li [user current-url current-path]
  (if (empty? @user)
    (sign-in-nav current-url current-path)
    (user-nav user current-url)
    ))

;#?(:clj (debug/debug-ns *ns*))
