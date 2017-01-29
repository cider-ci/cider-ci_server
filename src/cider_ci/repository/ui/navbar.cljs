(ns cider-ci.repository.ui.navbar
  (:require-macros
    [reagent.ratom :as ratom :refer [reaction]]
    )
  (:require
    [cider-ci.utils.core :refer [presence]]
    [cider-ci.client.state :as state]
    [cider-ci.repository.ui.navbar.user :as user-navbar]
    ))


(def emsp " ")
; watch out: this looks in coding-editors
; like a normal space but it is an unicode emspace

(def release (reaction (-> @state/server-state :release)))

(defn navbar []
  [:div.navbar.navbar-default {:role :navigation}
   [:div.container-fluid
    [:div.navbar-header
     [:a.navbar-brand {:href "/cider-ci/ui/public"}
      [:b
       [:span " Cider-CI "]
       [:span.edition (or (-> @release :edition presence) "")] " "
       (when-let [name (-> @release :name presence)]
         [:span.name emsp [:em name] emsp])
       [:span.semantic-version
        [:span.major (-> @release :version_major)]
        [:span.divider "."]
        [:span.minor (-> @release :version_minor)]
        [:span.divider "."]
        [:span.patch (-> @release :version_patch)]
        [:span (when-let [pre (-> @release :version_pre presence)]
                 [:span [:span.divider "-"] [:span.pre pre]])]]]]]
    [:div.navbar-left
     [:ul.navbar-nav.nav
      [:li [:a {:href "/cider-ci/ui/workspace"} [:i.fa.fa-dashboard] " Workspace"]]
      [:li [:a {:href "/cider-ci/repositories/projects/"} [:i.fa.fa-git-square] " Projects "]]
      [:li [:a {:href "/cider-ci/api/api-browser/index.html#/cider-ci/api"} [:i.fa.fa-magic] " API Browser "]]
      [:li [:a {:href "/cider-ci/docs/"}  [:i.fa.fa-file-text-o] " Documentation "] ]
      [user-navbar/admin-actions]
      ]]

    [:ul.nav.navbar-nav.navbar-right.user
     (user-navbar/li) ]]])
