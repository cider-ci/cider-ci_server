; Copyright © 2013 - 2017 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.server.trees.ui
  (:refer-clojure :exclude [str keyword])
  (:require-macros
    [reagent.ratom :as ratom :refer [reaction]]
    [cljs.core.async.macros :refer [go]]
    )
  (:require
    [cider-ci.constants :as constants :refer [GRID-COLS]]
    [cider-ci.server.client.breadcrumbs :as breadcrumbs]
    [cider-ci.server.client.connection.request :as request]
    [cider-ci.server.client.routes :as routes]
    [cider-ci.server.client.shared :refer [pre-component]]
    [cider-ci.server.client.state :as state]
    [cider-ci.server.client.utils :refer [humanize-datetime-component]]
    [cider-ci.server.trees.dependency-graph :as dependency-graph]
    [cider-ci.server.trees.ui-shared :as shared :refer [tree-id*]]
    [cider-ci.shared.icons :as icons]
    [cider-ci.utils.core :refer [keyword str presence]]
    [cider-ci.utils.markdown :as markdown]
    [cider-ci.utils.sha1]

    [cljs.pprint :refer [pprint]]
    [cljs.core.async :as async]
    [clojure.walk :as walk]
    [reagent.core :as reagent]
    [reagent.ratom :as ratom]
    ))

(defn page-is-active? []
  (= (-> @state/page-state :current-page :component)
     "cider-ci.server.trees.ui/page"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def jobs* (ratom/atom {}))

(def fetch-jobs-id* (ratom/atom nil))

(defn fetch-jobs []
  (let [resp-chan (async/chan)
        id (request/send-off
             {:url "/cider-ci/jobs/"
              :method :get
              :query-params {:tree-id (-> @tree-id* clj->js js/JSON.stringify)}}
             {:title "Fetch Jobs"
              :modal false}
             :chan resp-chan)]
    (reset! fetch-jobs-id* id)
    (go (let [resp (<! resp-chan)]
          (when (= (:status resp) 200)
            (reset! jobs* {@tree-id* (->> (:body resp)
                                          (into [])
                                          (map (fn [[_ v]] [(:key v) v]))
                                          (into {}))}))
          (js/setTimeout
            #(when (and (page-is-active?)
                        (= id @fetch-jobs-id*))
               (fetch-jobs))
            5000)))))

(defn job-state [job-key]
  (-> @jobs* (get @tree-id* {}) (get job-key {}) :state))

(def dependency-and-trigger-graph*
  (reaction
    (when-let [g (:hiccup-graph @dependency-graph/dependency-and-trigger-graph*)]
      (walk/prewalk (fn [n]
                      (or
                        (when-let [job-id  (and (vector? n)
                                                (= (first n) :g)
                                                (= "node" (-> n second :class))
                                                (-> n second :id))]
                          (when-let [job-state (job-state job-id)]
                            (let [new-n (update-in n [1 :class] (fn [current state]
                                                                  (str current " " state)) job-state)]
                              ;(js/console.log (with-out-str (pprint ["WALK NODE" job-id job-state new-n])))
                              new-n)))
                        n)) g))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn jobs-component []
  [:section.jobs
   [:h2 "Jobs"]
   [:table.jobs-table.table
    [:thead]
    [:tbody
     (doall
       (for [[_ job] (get @jobs* @tree-id* {})]
         [:tr.job
          {:key (:key job)
           :class (:state job)
           :id (:key job)}
          [:td
           [:a {:href (str "/cider-ci/ui/workspace/jobs/" (:id job))}
            [:span.label {:class (str "label-" (:state job))}
             [icons/state-icon (:state job)]]
            " "
            [:span.name
             (:name job)]]]
          [:td "created "
           [humanize-datetime-component (:created_at job)]]
          ]))]]])

(defn debug-component []
  (when (:debug @state/client-state)
    [:div.debug
     [:hr.clearfix]
     [:h2 "Page Debug"]
     [:div.tree-id
      [:h3 "@tree-id*"]
      (pre-component @tree-id*)]
     [:div.jobs
      [:h3 "@jobs*"]
      (pre-component @jobs*)]
     [:div.dependencies
      [:h3 "@dependency-graph/dependency-and-trigger-graph*"]
      (pre-component @dependency-graph/dependency-and-trigger-graph*)]
     [:div.dependencies
      [:h3 "@dependency-and-trigger-graph*"]
      (pre-component @dependency-and-trigger-graph*)]
     ]))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn graph-component []
  (reagent/create-class
    {:component-will-mount dependency-graph/setup-data
     :reagent-render (fn [_]
                       [:div.dependencies
                        [:h2 "Jobs - Dependency and Trigger Visualization"]
                        (if-let [dependency-and-trigger-graph  @dependency-and-trigger-graph*]
                          [:div.dependency-visualization dependency-and-trigger-graph]
                          [:div
                           [:alter.alert-info
                            "The dependency visualization is not available."]])])}))

(defn breadcrumbs-component []
  (breadcrumbs/breadcrumb-component
    [(breadcrumbs/home-li-component)
     (shared/tree-objects-breadcrumb-component @tree-id* :active? true)]
    [(shared/tree-objects-attachments-breadcrumb-component @tree-id*)
     (shared/project-configuration-breadcrumb-component @tree-id*)
     (shared/tree-objects-available-jobs-breadcrumb-component @tree-id*)]))

(defn title-component []
  [:div.title
   [:h1  "Jobs and Tree-Objects for "
    [shared/tree-id-compact-component @tree-id* {:full false}]]
   [:p [shared/tree-id-compact-component @tree-id* {:full true}]]])

(defn page-component []
  [:div.tree
   [breadcrumbs-component]
   [title-component]
   [jobs-component]
   [graph-component]
   [debug-component]
   ])


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn setup-page-data [_]
  (fetch-jobs))

(defn page []
  (reagent/create-class
    {:component-did-mount setup-page-data
     :reagent-render page-component
     }))
