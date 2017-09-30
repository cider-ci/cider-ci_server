(ns cider-ci.server.client.ui.navbar
  (:require
    [cider-ci.constants :as constants]
    [cider-ci.server.client.connection :as connection]
    [cider-ci.server.client.routes :as routes]
    [cider-ci.server.client.state :as state]
    [cider-ci.server.client.constants :refer [CONTEXT]]
    [cider-ci.server.session.password.ui :as session.password]
    [cider-ci.server.client.ui.debug :as debug]
    [cider-ci.server.client.ui.navbar.release :as navbar.release]
    [cider-ci.server.client.ui.navbar.user :as navbar.user]
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

(defn nav-li-commits []
  [:li.commits
   [:a {:href (routes/commits-path)}
    [:i.fa.fa-fw.fa-code-fork]
    (when @state/screen-tablet?*
      [:span constants/utf8-narrow-no-break-space "Commits"])]])

(defn nav-li-executors []
  [:li.executors
   [:a {:href (routes/executors-path {})}
    [:i.fa.fa-fw.fa-cog]
    (when @state/screen-tablet?*
      [:span constants/utf8-narrow-no-break-space "Executors"])]])

(defn nav-li-projects []
  [:li.projects
   [:a {:href "/cider-ci/repositories/projects/"}
    [:i.fa.fa-fw.fa-list-alt]
    (when @state/screen-tablet?*
      [:span constants/utf8-narrow-no-break-space "Projects"])]])

(defn nav-li-documentation []
  [:li.documentation
   [:a {:href "/cider-ci/docs/"}
    [:i.fa.fa-fw.fa-question-circle-o] constants/utf8-narrow-no-break-space "Documentation"]])

(defn nav-li-api-browser []
  [:li
   [:a {:href "/cider-ci/api/api-browser/index.html#/cider-ci/api"}
    [:i.fa.fw.fa-magic] constants/utf8-narrow-no-break-space "API-Browser"]])


(defn more-nav-dropdown [user*]
  [:li
   [:a.dropdown-toggle
    {:data-toggle "dropdown"
     :href "#"}
    [:span [:i.fa.fa-bars]
     (when @state/screen-tablet?*
       [:span constants/utf8-narrow-no-break-space "More"])
     [:b.caret]]]
   [:ul.dropdown-menu
    [:li [:a {:href "/cider-ci/client/"} " Root "]]
    [nav-li-documentation]
    [nav-li-api-browser]
    [:li.divider]
    [:li [:a {:href (str CONTEXT "/create-admin")} "Create Admin"]]
    [:li [:a {:href "/cider-ci/session/password/sign-in"} "Sign in"]]
    [:li [:a {:href "/cider-ci/client/welcome-page/edit"} "Edit Welcome Page"]]
    [:li.divider]
    [:li
     [:a {:href "/cider-ci/ui/admin/users"}
      [:i.fa.fa-fw.fa-users]
      " Users "]]
    [:li
     [:a
      {:href "/cider-ci/ui/admin/status "}
      [:i.fa.fa-fw.fa-dashboard]
      " Status "]]
    [:li.divider]
    [:li [:a {:href "#"} "Account"]]
    [:li [:a {:href (routes/user-api-tokens-path {:user-id (:id @user*)})} "API-Tokens" ]]
    [:li.divider]
    [:li [:a {:href (debug/path) } "Debug page"]]
    [:li [:a {:href "#"
              :on-click debug/toggle-debug}
          [:input {:type "checkbox"
                   ;:on-change debug/toggle-debug
                   :checked (:debug @state/client-state)}] "Toggle debug display" ]]]])

(defn application-nav [user*]
  (when @user*
    [:div.navbar-left.app-nav
     [:ul.navbar-nav.nav
      [nav-li-commits]
      [nav-li-executors]
      [nav-li-projects]
      [more-nav-dropdown user*]]]))

(defn user-nav [user* current-url*]
  (when @user*
    [:div.navbar-right
     [:ul#user-actions.nav.navbar-nav.user
      [navbar.user/li user* current-url*]
      [:li {:class @connection/socket-bg-color-class*}
       [:a {:href (routes/socket-path)}
        [connection/socket-icon-component]
        (when @state/screen-desktop?*
          " Socket ")]]
      [:li
       [:a {:href (routes/requests-path)}
        [connection/requests-icon-component]
        (when @state/screen-desktop?*
          " Requests ")]]]]))

(defn navbar [user* current-url* auth-providers]
  [:div
   [application-nav user*]
   [user-nav user* current-url*]
   [:div.sign-ins
    (when-not @user*
      [sign-ins current-url* auth-providers])]])
