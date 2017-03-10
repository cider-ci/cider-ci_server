(ns cider-ci.repository.ui.projects.edit-new.triggers
  (:require-macros
    [reagent.ratom :as ratom :refer [reaction]]
    )
  (:require
    [cider-ci.repository.constants :refer [CONTEXT]]
    [cider-ci.repository.ui.projects.edit-new.shared :refer [form-data dissected-git-url id project update-form-data update-form-data-value section]]
    [cider-ci.utils.core :refer [presence]]
    ))


(defn branch-trigger-include-match  []
  [:div.form-group
   [:label "Branch trigger include-match"]
   [:input#branch_trigger_include_match.form-control
    {:on-change #(update-form-data-value :branch_trigger_include_match (-> % .-target .-value presence))
     :value (-> @form-data :branch_trigger_include_match) }]])

(defn branch-trigger-exclude-match  []
  [:div.form-group
   [:label "Branch trigger exclude-match"]
   [:input#branch_trigger_exclude_match.form-control
    {:on-change #(update-form-data-value :branch_trigger_exclude_match (-> % .-target .-value presence))
     :value (-> @form-data :branch_trigger_exclude_match) }]])

(defn branch-trigger-max-commit-age []
  [:div.form-group
   [:label "Branch trigger maximal commit age"]
   [:input#branch_trigger_max_commit_age.form-control
    {:on-change #(update-form-data-value :branch_trigger_max_commit_age (-> % .-target .-value presence))
     :value (-> @form-data :branch_trigger_max_commit_age) }]
   [:p.help-block "The prevalent use case for this setting is to prevent (re-) triggering all the jobs
                    on old and outdated branches when you add a new repository or set up a new
                    Cider-CI instance. "]])

(defn cron-trigger-enabled []
  [:div.form-group
   [:label "Cron trigger"]
   [:div.checkbox
    [:label
     [:input#cron_trigger_enabled
      {:type :checkbox
       ;:defaultChecked (-> @form-data :public_view_permission)
       :checked (-> @form-data :cron_trigger_enabled)
       :on-change #(update-form-data (fn [fd] (assoc fd :cron_trigger_enabled (-> fd :cron_trigger_enabled not))))
       }]
     "Cron trigger enabled"]
    [:p.help-block
     "If this is checked jobs will be (re)run according to the cron setting in the project configuration."]]])


(defn component []
  [section "Triggers"
   (fn [] [:div
           [branch-trigger-include-match]
           [branch-trigger-exclude-match]
           [branch-trigger-max-commit-age]
           [cron-trigger-enabled]])
   :description "You can override the triggers
                defined in the project configuration here."])


