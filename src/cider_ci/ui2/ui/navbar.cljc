(ns cider-ci.ui2.ui.navbar
  (:require
    [cider-ci.ui2.ui.navbar.release :as navbar.release]
    [cider-ci.ui2.ui.navbar.user :as navbar.user]
    [cider-ci.ui2.constants :refer [CONTEXT]]
    [cider-ci.utils.core :refer [presence]]
    #?(:clj [logbug.debug :as debug])
    ))

(defn navbar [release user current-url current-path]
  [:div.navbar.navbar-default {:role :navigation}
   [:div.container-fluid
    [:div.navbar-header
     [:a.navbar-brand {:href "/cider-ci/ui2/"}
      (navbar.release/navbar-release release)]]
    [:div.navbar-left
     [:ul.navbar-nav.nav
      [:li
       [:a.dropdown-toggle
        {:data-toggle "dropdown"
         :href "/cider-ci/ui2/"}
        [:span [:i.fa.fa-globe] " UI2" [:em " Beta!"]
         [:b.caret]]]
       [:ul.dropdown-menu
        [:li [:a {:href "/cider-ci/ui2/"} "Root"]]
        [:li.divider]
        [:li [:a {:href (str CONTEXT "/create-admin")} "Create Admin"]]
        [:li [:a {:href "/cider-ci/ui2/session/password-sign-in"} "Sign in"]]
        [:li [:a {:href "/cider-ci/ui2/welcome-page/edit"} "Edit Welcome Page"]]
        [:li.divider]
        [:li [:a {:href "/cider-ci/ui2/debug"} "Debug"]]]]
      [:li [:a {:href "/cider-ci/ui/workspace"} [:i.fa.fa-dashboard] " Workspace"]]
      [:li [:a {:href "/cider-ci/repositories/projects/"} [:i.fa.fa-git-square] " Projects "]]
      [:li [:a {:href "/cider-ci/api/api-browser/index.html#/cider-ci/api"} [:i.fa.fa-magic] " API Browser "]]
      [:li [:a {:href "/cider-ci/docs/"}  [:i.fa.fa-file-text-o] " Documentation "]]
      (navbar.user/admin-actions user)
      ]]
    [:ul.nav.navbar-nav.navbar-right.user
     (navbar.user/li user current-url current-path)
     ]]])

;#?(:clj (debug/debug-ns *ns*))
