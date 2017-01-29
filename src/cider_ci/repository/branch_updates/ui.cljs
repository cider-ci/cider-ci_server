(ns cider-ci.repository.branches.ui
  (:require
    [cider-ci.repository.ui.projects.shared :refer [humanize-datetime]]
    [cider-ci.repository.constants :refer [CONTEXT]]
    [cider-ci.repository.ui.request :as request]
    [cider-ci.client.state :as state]

    [clojure.contrib.inflect :refer [pluralize-noun]]
    [reagent.core :as r]
    ))

(defn color-class [project]
  (let [state (-> project :branch-updates :state)]
    (condp contains? state
      #{"ok"} "success"
      #{"error"} "danger"
      #{"updating" "waiting"} "executing"
      #{"initializing"} "default"
      "warning")))

(defn state-icon [project]
  (r/create-class
    {:component-did-mount #(.tooltip (js/$ (reagent.core/dom-node %)))
     :reagent-render
     (fn [project]
       (let [state (-> project :branch-updates :state)]
         [:a {:href "#" :data-toggle "tooltip" :title state :data-original-title state}
          [:span {:class state}
           (condp contains? state
             #{"ok"} [:i.fa.fa-fw.fa-check-circle.text-success]
             #{"updating"} [:i.fa.fa-fw.fa-refresh.cog.fa-spin.text-warning]
             #{"waiting" "initializing"} [:i.fa.fa-fw.fa-spinner.fa-pulse.text-warning]
             #{"error"}[:i.fa.fa-fw.fa-warning.text-danger]
             [:i.fa.fa-fw.fa-question-circle.text-warning])]]))}))

(defn th []
  [:th.text-center.branches
   " Branches "])

(defn send-update-branches [project]
  (let [id (-> project :id name)
        req {:url (str CONTEXT "/projects/" id "/update-branches")}]
    (request/send-off2
      req
      {:title (str "Update branches \"" (:name project) "\"")})))

(defn button [project]
  (let [title "Update branches"]
    (r/create-class
      {:component-did-mount #(.tooltip (js/$ (reagent.core/dom-node %)))
       :reagent-render
       (fn [id]
         [:a.btn.btn-default.btn-xs.pull-right
          {:href "#" :on-click #(send-update-branches project)
           :data-toggle "tooltip" :title title
           :data-original-title title}
          [:i.fa.fa-refresh] " "])})))

(defn td [project]
  [:td.text-center.branches
   {:class (color-class project)
    :data-state (-> project :branch-updates :state)}
   [:span [state-icon project] " "]
   [:span (humanize-datetime (:timestamp @state/client-state)
                             (-> project :branch-updates :updated_at))]
   [:span [button project]]])

(defn page-section [project]
  (let [branch-updates (-> project :branch-updates)]
    [:section.branch-updates
     [:h3 [:span [state-icon project]] "Updating and Importing Branches"]
     (when-let [at (:branches_updated_at branch-updates)]
       [:p.text-success "The branches have been updated "
        (humanize-datetime (:timestamp @state/client-state) at) "."])
     (when-let [at (:last_error_at branch-updates)]
       [:p.text-warning "The last failed update occurred "
        (humanize-datetime (:timestamp @state/client-state) at) "."])
     (when-let [branches-count (:branches_count branch-updates)]
       [:span
        "There " (if (< 1 branches-count) "are" "is" )
        [:a {:href (str "/cider-ci/ui/workspace?depth=0&repository_name=" (js/escape (:name project)))}
         (str " " branches-count " " (pluralize-noun branches-count "branch"))] "."])
     [:span
      (when-let [last-commited-at (:last_commited_at branch-updates)]
        [:span " The last commit is from "
         (humanize-datetime (:timestamp @state/client-state) last-commited-at) "."])]
     ]))
