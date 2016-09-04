(ns cider-ci.repository.components.navbar
  (:require-macros
    [reagent.ratom :as ratom :refer [reaction]]
    )
  (:require
    [cider-ci.utils.core :refer [presence]]
    [cider-ci.repository.state :as state]
    ))


(defn toggle-debug []
  (swap! state/client-state
         (fn [cs]
          (assoc cs :debug (not (:debug cs))))))

(def release (reaction (-> @state/server-state :release)))

(defn navbar []
  [:div.navbar.navbar-default {:role :navigation}
   [:div.container-fluid
    [:div.navbar-header
     [:a.navbar-brand {:href "/cider-ci/ui/public"}
      [:b [:span " Cider-CI "]]
      [:span.edition (or (-> @release :edition presence) "DEV")] " "
      [:span.semantiv-version
       [:span.major (-> @release :version_major)]
       [:span.divider "."]
       [:span.minor (-> @release :version_minor)]
       [:span.divider "."]
       [:span.patch (-> @release :version_patch)]
       [:span (when-let [pre (-> @release :version_pre presence)]
                [:span [:span.divider "-"] [:span.pre pre]])]]]]
    [:div.navbar-left
     [:ul.navbar-nav.nav
      [:li [:a {:href "/cider-ci/ui/workspace"} [:i.fa.fa-dashboard] " Workspace"]]
      [:li [:a {:href "/cider-ci/repositories/projects/"} [:i.fa.fa-git-square] " Projects "]]

      ]]

    [:form.navbar-form.navbar-right
     [:div.checkbox.navbar-btn
      [:label.navbar-link
       [:input {:type "checkbox"
                :on-change toggle-debug
                :checked (:debug @state/client-state)
                }] "Debug"]]
     ]]])
