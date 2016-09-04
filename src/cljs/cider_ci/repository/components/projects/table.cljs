(ns cider-ci.repository.components.projects.table
  (:require-macros
    [reagent.ratom :as ratom :refer [reaction]]
    )
  (:require
    [cider-ci.repository.constants :refer [CONTEXT]]
    [cider-ci.repository.state :as state]

    [accountant.core :as accountant]
    [clojure.contrib.humanize]
    [clojure.contrib.inflect :refer [pluralize-noun]]
    ))


(def order (reaction (or (-> @state/client-state :current-page :query-params :order)
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

(defn state-icon [project]
  (fn [project]
    (cond
      (not= (:state project) "idle") [:i.fa.fa-refresh.fa-spin.text-warning]
      (not (empty? (:issues project))) [:i.fa.fa-warning.text-danger]
      :else [:i.fa.fa-check-circle.text-success]
      )))

(defn row-contextual-class [project]
  (cond
    (not= (:state project) "idle") "warning"
    (not (empty? (:issues project))) "danger"
    :else "success"))

(defn humanize-datetime [_ dt]
  ;(clojure.contrib.humanize/datetime dt)
  (.to (js/moment) dt))

(defn row [id project]
  (fn [id project]
    [:tr.text-center {:class (row-contextual-class project)}
     [:td.name
      [:a {:href (str CONTEXT "/projects/" (:id project))}
       (:name project)]]
     [:td.last_fetched
      [state-icon project] " "
      (if-let [last-fetched-at (:last_fetched_at project)]
        [:span
         (humanize-datetime
           (:timestamp @state/client-state)
           last-fetched-at)]
        [:span "-"])]
     [:td.branches
      [:span.updated.updated.updated.updated
       (if-let [last-commit-at (:last_commited_at project)]
         (humanize-datetime
           (:timestamp @state/client-state)
           last-commit-at) "-")]
      [:span " / "]
      [:span.count
       (if-let [c (:branches_count project)]
         [:a {:href (str "/cider-ci/ui/workspace?depth=0&repository_name=" (js/escape (:name project)))}
          (str c " " (pluralize-noun c "branch"))] "-")]]]))

(defn table []
    [:div
     [:table.table.table-striped.projects
      [:thead
       [:tr.text-center {:key :thr}
        [:th.text-center {:key :name }
         [:div {:class "form-inline"}
          " Name "
          (if-not (= @order "name")
            [:a.btn.btn-default.btn-xs {:href "?order=name"}
             [:i.fa.fa-sort-alpha-asc]]
            [:a.btn.btn-default.btn-xs {:disabled true}
             [:i.fa.fa-sort-alpha-asc]])]]
        [:th.text-center {:colSpan "1"}
         " Last fetch "
         (if-not (= @order "fetched")
           [:a.btn.btn-default.btn-xs {:href "?order=fetched"}
            [:i.fa.fa-sort-amount-desc]]
           [:a.btn.btn-default.btn-xs {:disabled true}
            [:i.fa.fa-sort-amount-desc]])]
        [:th.text-center.branches
         " Last commit "
         (if-not (= @order "last_committed_at")
           [:a.btn.btn-default.btn-xs {:href "?order=last_committed_at"}
            [:i.fa.fa-sort-amount-desc]]
           [:a.btn.btn-default.btn-xs {:disabled true}
            [:i.fa.fa-sort-amount-desc]])]]]
      [:tbody
       (for [[id project] (->> @state/db
                               :repositories
                               (sort-by (fn [[k v]] (apply @sort-fn [v]))))]
         ^{:key (str "project_row_" id)} [row id project])]]])
