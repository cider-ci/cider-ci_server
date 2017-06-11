(ns cider-ci.server.repository.ui.projects.edit-new.permissions
  (:require-macros
    [reagent.ratom :as ratom :refer [reaction]]
    )
  (:require
    [cider-ci.server.repository.constants :refer [CONTEXT]]
    [cider-ci.server.repository.ui.projects.edit-new.shared :refer [form-data dissected-git-url id project update-form-data update-form-data-value section]]
    [cider-ci.utils.core :refer [presence]]
    ))


(defn permissions-input-fields []
  [:div.checkbox
   [:label
    [:input
     {:type :checkbox
      ;:defaultChecked (-> @form-data :public_view_permission)
      :checked (-> @form-data :public_view_permission)
      :on-change #(update-form-data (fn [fd] (assoc fd :public_view_permission (-> fd :public_view_permission not))))
      }]
    "Public view permission"]
   [:p.help-block
    "If this is checked the status of jobs, tasks, and trials belonging
    to this project can be " [:b " viewed " ]"  without being singed in. "
    "Visitors can also " [:b "download" ]" trials- and tree-attachments without being signed in. "
    "This needs to be "[:b "checked"]" if you use "[:b "badges"]"." ]])

(defn component []
  [section "Permissions"
   permissions-input-fields
   :description "Set the public visibility."])


