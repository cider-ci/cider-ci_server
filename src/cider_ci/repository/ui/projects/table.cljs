(ns cider-ci.repository.ui.projects.table
  (:require-macros
    [reagent.ratom :as ratom :refer [reaction]]
    )
  (:require
    [cider-ci.repository.branches.ui :as branches]
    [cider-ci.repository.fetch-and-update.ui :as fetch-and-update]
    [cider-ci.repository.push-hooks.ui :as push-hooks]
    [cider-ci.repository.push-notifications.ui :as push-notifications]
    [cider-ci.repository.ui.projects.shared :refer [humanize-datetime]]
    [cider-ci.repository.status-pushes.ui :as status-pushes]
    [cider-ci.repository.constants :refer [CONTEXT]]
    [cider-ci.client.state :as state]

    [accountant.core :as accountant]
    [clojure.contrib.humanize]
    [clojure.contrib.inflect :refer [pluralize-noun]]
    [reagent.core :as r]
    ))


;### sorting ##################################################################

(def order (reaction (or (-> @state/page-state :current-page :query-params :order)
                         "name")))

(defn most-recent-first-sort-fn [o]
  (- (.unix (cond
              (string? o) (js/moment o)
              :else (js/moment)))))

(def sort-fn
  (reaction
    (case @order
      "name" (fn [project] (:name project))
      "fetched" (fn [project] (most-recent-first-sort-fn (:last_fetched_at project)))
      "last_committed_at" (fn [project] (most-recent-first-sort-fn (:last_commited_at project)))
      (fn [project] (:name project)))))


;##############################################################################

(defn name-color-class [project]
  (let [cell-classes (map (fn [fun] (apply fun [project]))
                          [branches/color-class
                           fetch-and-update/color-class
                           push-notifications/color-class
                           push-hooks/color-class
                           status-pushes/color-class
                           ])]
    (cond
      (some #{"executing" "active"} cell-classes) "executing"
      (some #{"danger"} cell-classes) "danger"
      (every? #{"success" "default"} cell-classes) "success"
      :else "warning")))

(defn row [id project]
  (fn [id project]
    [:tr.text-center
     [:td.name {:class (name-color-class project)}
      [:a {:href (str CONTEXT "/projects/" (:id project))}
       (:name project)]]
     [branches/td project]
     [fetch-and-update/td project]
     [push-notifications/td project]
     [push-hooks/td project]
     [status-pushes/td project]]))


;##############################################################################

(defn th-name []
  [:th.text-center {:key :name }
   [:div {:class "form-inline"}
    " Name " ]])


;##############################################################################

(defn table []
  [:div
   [:table.table.table-striped.table-projects
    [:thead
     [:tr.text-center {:key :thr}
      [th-name]
      [branches/th]
      [fetch-and-update/th]
      [push-notifications/th]
      [push-hooks/th]
      [status-pushes/th]
      ]]
    [:tbody.table-bordered
     (for [[id project] (->> @state/server-state
                             :repositories
                             (sort-by (fn [[k v]] (apply @sort-fn [v]))))]
       ^{:key (str "project_row_" id)} [row id project])]]])
