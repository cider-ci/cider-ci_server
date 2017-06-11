(ns cider-ci.server.repository.ui.projects.edit-new.basics
  (:require-macros
    [reagent.ratom :as ratom :refer [reaction]]
    )
  (:require
    [cider-ci.server.repository.constants :refer [CONTEXT]]
    [cider-ci.server.repository.ui.projects.edit-new.shared :refer [form-data dissected-git-url id project update-form-data update-form-data-value section]]
    [cider-ci.utils.core :refer [presence]]
    ))

(def git-url-valid?
  (reaction
    (-> @form-data :git_url clojure.string/blank? not)))

(def git-url-input-state
  (reaction
    (cond
      (not @git-url-valid?) "has-error"
      :else "")))

(def name-valid?
  (reaction
    (-> @form-data :name clojure.string/blank? not)))

(def name-input-state
  (reaction
    (cond
      (not @name-valid?) "has-error"
      :else "")))

(defn git-url-input []
  [:div.form-group {:class @git-url-input-state}
   [:label "Git url"]
   [:input#git_url.form-control
    {:placeholder "https://github.com/my/project.git"
     :on-change #(update-form-data-value :git_url (-> % .-target .-value presence))
     :value (-> @form-data :git_url)}]])

(defn name-input []
  [:div.form-group {:class @name-input-state}
   [:label "Project name"]
   [:input#name.form-control
    {:placeholder (or (-> @dissected-git-url :project_name) "My Project" )
     :on-change #(update-form-data-value :name (-> % .-target .-value presence))
     :on-focus #(when-let [suggested-project-name (:project_name @dissected-git-url)]
                  (when (clojure.string/blank? (:name @form-data))
                    (update-form-data-value :name suggested-project-name)))
     :value (-> @form-data :name)}]
   [:p.help-block "An unique and mnemonic name."]])

(defn fields []
  [:div
   [git-url-input]
   [name-input]])

(defn component []
  [section "Basic Settings" fields])
