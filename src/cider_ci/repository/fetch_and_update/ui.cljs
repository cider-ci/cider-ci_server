(ns cider-ci.repository.fetch-and-update.ui
  (:require-macros
    [reagent.ratom :as ratom :refer [reaction]]
    [cljs.core.async.macros :refer [go]]
    )
  (:require
    [cider-ci.repository.ui.projects.shared :refer [humanize-datetime]]
    [cider-ci.repository.constants :refer [CONTEXT]]
    [cider-ci.repository.ui.request :as request]
    [cider-ci.client.state :as state]

    [cider-ci.utils.core :refer [presence]]
    [cider-ci.utils.url]

    [accountant.core :as accountant]
    [cljs.pprint :refer [pprint]]
    [reagent.core :as r]
    ))

(defn state-icon [project]
  (r/create-class
    {:component-did-mount #(.tooltip (js/$ (reagent.core/dom-node %)))
     :reagent-render
     (fn [project]
       (let [state (-> project :fetch-and-update :state)]
         [:a {:href "#" :data-toggle "tooltip" :title state :data-original-title state}
          [:span {:class state}
           (condp contains? state
             #{"ok"} [:i.fa.fa-fw.fa-check-circle.text-success]
             #{"fetching"} [:i.fa.fa-fw.fa-refresh.fa-spin.text-warning]
             #{"updating"} [:i.fa.fa-fw.fa-cog.fa-spin.text-warning]
             #{"waiting" "initializing"} [:i.fa.fa-fw.fa-spinner.fa-pulse.text-warning]
             #{"error"}[:i.fa.fa-fw.fa-warning.text-danger]
             [:i.fa.fa-fw.fa-question-circle.text-warning])]]))}))

(defn color-class [project]
  (let [state (-> project :fetch-and-update :state)]
    (condp contains? state
      #{"ok"} "success"
      #{"fetching" "updating" "waiting" "initializing"} "executing"
      #{"error"} "danger"
      "danger"
      )))

(defn fetch [project]
  (let [id (-> project :id name)
        req {:url (str CONTEXT "/projects/" id "/fetch")}]
    (request/send-off2
      req
      {:title (str "Fetch \"" (:name project) "\"")})))

(defn button [project]
  (let [title "Fetch now"]
    (r/create-class
      {:component-did-mount #(.tooltip (js/$ (reagent.core/dom-node %)))
       :reagent-render
       (fn [id]
         [:a.btn.btn-default.btn-xs.pull-right.fetch-now
          {:href "#" :on-click #(fetch project)
           :data-toggle "tooltip" :title title
           :data-original-title title}
          [:i.fa.fa-download] " "])})))

(defn th []
  [:th.text-center {:colSpan "1"}
   " Fetching " ])

(defn td [project]
  [:td.fetch-and-update.fetching.last_fetched.text-center
   {:class (color-class project)
    :data-state (-> project :fetch-and-update :state)
    :data-last-fetched-at (-> project :fetch-and-update :last_fetched_at)}
   [state-icon project] " "
   (if-let [last-fetched-at (-> project :fetch-and-update :last_fetched_at)]
     [:span
      (humanize-datetime (:timestamp @state/client-state) last-fetched-at)
      ]
     [:span "-"])
   " "
   [button project]])

(defn page-section [project]
  (let [now-timestap (:timestamp @state/client-state)
        fetch-and-update (:fetch-and-update project)
        last-fetched-at (:last_fetched_at fetch-and-update)
        last-fetched-at-ago (when (and now-timestap last-fetched-at)
                              (.to now-timestap last-fetched-at))
        last-fetch-failed-at (:last_error_at fetch-and-update)
        last-fetch-failed-at-ago (when last-fetch-failed-at (.to (js/moment) last-fetch-failed-at))]
    [:section.fetch-and-update
     [:h3 [:span [state-icon project]] "Fetching the Remote"]
     [:p "The remote URL for this project is " [:code (:git_url project)] ". "]
     [:p
      (when last-fetched-at-ago
        [:span.text-success "The repository has been " [:b "fetched " last-fetched-at-ago ]
         ". "])
      (when last-fetch-failed-at-ago
        [:span.text-warning "The last failed fetch happened " last-fetch-failed-at-ago ". "])]
     ]))

(defn error-alert [project]
  [:div#fetch-and-update-error.error
   (when-let [error (:fetch_and_update_error project)]
     [:div.alert.alert-danger
      [:h4 "Fetch and Update Error"]
      [:pre error]])])
