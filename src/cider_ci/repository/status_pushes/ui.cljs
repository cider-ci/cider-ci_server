(ns cider-ci.repository.status-pushes.ui
  (:require-macros
    [reagent.ratom :as ratom :refer [reaction]]
    [cljs.core.async.macros :refer [go]]
    )
  (:require
    [cider-ci.repository.ui.projects.shared :refer [humanize-datetime]]
    [cider-ci.repository.constants :refer [CONTEXT]]
    [cider-ci.repository.remote :as remote]
    [cider-ci.repository.ui.request :as request]
    [cider-ci.client.state :as state]
    [cider-ci.repository.remote :refer [api-access?]]

    [cider-ci.utils.core :refer [presence]]
    [cider-ci.utils.url]

    [accountant.core :as accountant]
    [cljs.pprint :refer [pprint]]
    [reagent.core :as r]
    ))


(defn disabled? [project]
  (-> project :send_status_notifications not))

(defn state [project]
  (cond
    (disabled? project) "disabled"
    (-> project api-access? not) "unaccessible"
    :else (-> project :status-pushes :state)))

(defn push-statuses [project]
  (let [id (-> project :id name)
        req {:url (str CONTEXT "/projects/" id "/push-statuses")}]
    (request/send-off2
      req
      {:title (str "Pushing statuses for \"" (:name project) "\". "
                   " The request has been accepted. "
                   " Pushing is carried out asynchronously. "
                   " Check the state of \"Status-Pushes\". ")})))

(defn send-button [project]
  (let [title "Push all job statuses for this project now."]
    (r/create-class
      {:component-did-mount #(.tooltip (js/$ (reagent.core/dom-node %)))
       :reagent-render
       (fn [project]
         [:button.push-statuses.btn.btn-default.btn-xs.pull-right
          {:on-click #(push-statuses project)
           :data-toggle "tooltip" :title title
           :data-original-title title}
          [:i.fa.fa-send]])})))

(defn- color-class [project]
  (condp contains? (state project)
    #{"ok"} "success"
    #{"unused" "disabled"} "default"
    #{"unavailable"} "warning"
    #{"posting" "waiting"} "executing"
    #{"error"} "danger"
    "danger"))

(defn state-icon [project]
  (r/create-class
    {:component-did-mount #(.tooltip (js/$ (reagent.core/dom-node %)))
     :reagent-render
     (fn [project]
       (let [state (state project)]
         [:a {:href "#" :data-toggle "tooltip" :title state :data-original-title state}
          [:span {:class state}
           (condp contains? state
             #{"ok"} [:i.fa.fa-fw.fa-check-circle.text-success]
             #{"unused" "disabled"} [:i.fa.fa-fw.fa-circle-o.text-muted]
             #{"posting"} [:i.fa.fa-fw.fa-cog.fa-spin.text-executing]
             #{"waiting"} [:i.fa.fa-fw.fa-spinner.fa-pulse.text-executing]
             #{"unaccessible"} [:i.fa.fa-fw.fa-warning.text-danger]
             #{"error"}[:i.fa.fa-fw.fa-warning.text-danger]
             [:i.fa.fa-fw.fa-question-circle.text-warning])]]))}))


(defn error-link [project]
  (if (-> project :status-pushes :error)
    [:span.error-link
     [:a {:href (str CONTEXT "/projects/"
                     (:id project) "/issue/"
                     (->> [:status-pushes :error]
                          clj->js (.stringify js/JSON) js/encodeURIComponent))}
      " Error "]]
    [:span]))

(defn th []
  [:th.text-center {:colSpan "1"}
   " Status-Pushes " ])

(defn td [project]
  (let [state (state project)]
    [:td.status-pushes.text-center
     {:class (color-class project)
      :data-state state
      :data-last-posted-at (-> project :status-pushes :last_posted_at)}
     [:span [state-icon project] " "]
     [:span (condp contains? state
              #{"ok"} [:span (when-let [at (-> project :status-pushes :updated_at)]
                               (humanize-datetime (:timestamp @state/client-state) at))]
              (str " " state " "))]
     [:span
      (when (and (-> project disabled? not)
                 (-> project api-access?))
        [send-button project])]]))

(defn page-section [project]
  [:section.push-hooks
   [:h3 [:span [state-icon project]] "Status-Pushes"]
   [:div
    (condp contains? (state project)
      #{"disabled"} [:p "Status-Pushes are disabled! "
                     "The outcome of jobs will not be sent to the remote."]
      #{"unaccessible"} [:p.text-danger "Status-pushes are enabled but the configuration is not sufficient to do so!"]
      #{"unused"} [:p.text-default "Status-pushes are enabled but there no recent statuses to push."]
      #{"error"} [:p.text-danger "Pushing the last status to the remove failed!"]
      #{"ok"} [:p.text-success "The last status push succeeded "
               (when-let [at (-> project :push-hook :updated_at)]
                 (humanize-datetime (:timestamp @state/client-state) at)) "."]
      [:p "???"])]])

