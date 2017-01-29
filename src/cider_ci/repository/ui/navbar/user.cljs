(ns cider-ci.repository.ui.navbar.user
  (:require-macros
    [reagent.ratom :as ratom :refer [reaction]]
    )
  (:require
    [cider-ci.utils.core :refer [presence]]
    [cider-ci.repository.constants :refer [CONTEXT]]
    [cider-ci.client.state :as state]
    ))

(def user (reaction (-> @state/server-state :user presence)))

(defn toggle-debug []
  (swap! state/client-state
         (fn [cs]
          (assoc cs :debug (not (:debug cs))))))

(defn admin-actions []
  (when true (:is_admin @user)
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

(defn li []
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
      [:li
       [:form
        {:action "/cider-ci/ui/public/sign_out"
         :method :post}
        [:button.btn.btn-warning.nabar-btn {:type "submit"}
         [:span " Sign out! "
          [:i.fa.fa-sign-out]]]]]
      [:li
       [:a {:href "/cider-ci/ui/workspace/account/edit"}
        [:i.fw.fa.fa-pencil]
        " Account " ]]
      [:li.divider]
      [:li
       [:input {:style {:margin-left "1em"}
                :type "checkbox"
                :on-change toggle-debug
                :checked (:debug @state/client-state)
                }] " Debug state"]
      [:li [:a {:href (str CONTEXT "/ui/debug")} " Debug Page "]]]]))



