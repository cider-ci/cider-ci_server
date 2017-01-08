(ns cider-ci.ui2.ui.navbar
  (:require
    [cider-ci.ui2.ui.navbar.release :as navbar.release]
    [cider-ci.ui2.ui.navbar.user :as navbar.user]
    [cider-ci.ui2.constants :refer [CONTEXT]]
    [cider-ci.ui2.session.password.ui :as session.password]

    [cider-ci.utils.core :refer [presence]]
    ))

(defn use-current-url-for-redirect? [current-url]
  (and current-url (not (re-matches  #".*create-admin$" current-url))))

(defn url-for-redirect [current-url]
  (if (use-current-url-for-redirect? current-url)
    current-url (str CONTEXT "/")))

(defn password-sign-in-button [current-url]
  [:a.btn.btn-info
   {:href (str (session.password/sign-in-path)
               (str "?url=" (js/encodeURIComponent
                              (url-for-redirect current-url))))}
   [:i.fa.fa-sign-in.fa-fw]
   " Sign in with password"])

(defn sign-ins [current-url_r auth-providers_r]
  [:div
   [:div.navbar-form.navbar-right
    [password-sign-in-button @current-url_r]]
   [:div
    (for [[provider-key provider-name] (or (seq @auth-providers_r) [])]
      [:form.navbar-form.navbar-right
       {:action (str CONTEXT "/session/oauth/request-sign-in")
        :method :post}
       [:input {:name :provider-key
                :type :hidden
                :value provider-key}]
       [:input {:name :current-url
                :type :hidden
                :value (url-for-redirect @current-url_r)}]
       [:button.btn.btn-info
        {:type :submit}
        [:i.fa.fa-sign-in.fa-fw]
        [:span "Sign in via "
         [:b provider-name]]]])]])

(defn navbar [user current-url auth-providers]
  [:div
   (when (-> @user empty? not)
     [:div.navbar-left
      [:ul.navbar-nav.nav
       [:li
        [:a.dropdown-toggle
         {:data-toggle "dropdown"
          :href "#"}
         [:span [:i.fa.fa-globe] " UI2" [:em " Beta!"]
          [:b.caret]]]
        [:ul.dropdown-menu
         [:li [:a {:href "/cider-ci/ui2/"} "Root"]]
         [:li.divider]
         [:li [:a {:href (str CONTEXT "/create-admin")} "Create Admin"]]
         [:li [:a {:href "/cider-ci/ui2/session/password/sign-in"} "Sign in"]]
         [:li [:a {:href "/cider-ci/ui2/welcome-page/edit"} "Edit Welcome Page"]]
         [:li.divider]
         [:li [:a {:href "/cider-ci/ui2/debug"} "Debug"]]]]
       [:li [:a {:href "/cider-ci/ui/workspace"} [:i.fa.fa-dashboard] " Workspace"]]
       [:li [:a {:href "/cider-ci/repositories/projects/"} [:i.fa.fa-git-square] " Projects "]]
       [:li [:a {:href "/cider-ci/api/api-browser/index.html#/cider-ci/api"} [:i.fa.fa-magic] " API Browser "]]
       [:li [:a {:href "/cider-ci/docs/"}  [:i.fa.fa-file-text-o] " Documentation "]]
       (navbar.user/admin-actions user)]])
   (when-not (empty? @user)
     [:ul.nav.navbar-nav.navbar-right.user (navbar.user/li user current-url)])
   [:div.sign-ins
    (when (empty? @user)
      [sign-ins current-url auth-providers])]])
