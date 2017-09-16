(ns cider-ci.server.trees.ui.project-configuration
  (:refer-clojure :exclude [str keyword])
  (:require-macros
    [reagent.ratom :as ratom :refer [reaction]]
    [cljs.core.async.macros :refer [go]]
    )
  (:require
    [cider-ci.server.client.connection.request :as request]
    [cider-ci.server.client.routes :as routes]
    [cider-ci.server.client.state :as state]
    [cider-ci.server.trees.ui-shared :as shared :refer [tree-id*]]
    [cider-ci.server.trees.dependency-graph :as dependency-graph :refer [tree-id*]]
    [cider-ci.server.ui2.shared :refer [pre-component pre-component-fipp pre-component-pprint]]
    [cider-ci.utils.core :refer [keyword str presence]]
    [cider-ci.utils.markdown :as markdown]
    [cider-ci.utils.sha1]

    [cljs.pprint :refer [pprint]]
    [fipp.edn :refer [pprint] :rename {pprint fipp}]
    [loom.attr]
    [loom.graph]
    [loom.io]

    [cljs.core.async :as async]
    [reagent.ratom :as ratom]
    [reagent.core :as reagent]
    ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn debug-component []
  (when (:debug @state/client-state)
    [:div.debug
     [:hr.clearfix]
     [:h2 "Page Debug"]
     [:div.tree-id
      [:h3 "@tree-id*"]
      (pre-component @tree-id*)]
     [:div.dependency-and-trigger-graph
      [:h3 "@dependency-graph/dependency-and-trigger-graph*"]
      [:pre (with-out-str (cljs.pprint/pprint @dependency-graph/dependency-and-trigger-graph*))]]
     (when-let [gv-source-graph (-> @ dependency-graph/dependency-and-trigger-graph* :gv-source-graph presence)]
       [:div.gv-source-graph
        [:h3 "gv-source-graph"]
        [:pre gv-source-graph]])
     [:div.project-configurations
      [:h3 "@dependency-graph/project-configurations*"]
      (pre-component-pprint @dependency-graph/project-configurations*)]
     ]))

(defn project-configuration-component []
  [:div
   [:h2 "Normalized Project Configuration in JSON-Format"]
   (if-let [pc (get @dependency-graph/project-configurations* @tree-id* nil)]
     (pre-component pc)
     [:alert.alert-warning
      [:p "There is currently no project configuration available for this tree-id. "
       "Check the requests and possibly reload the page manually. "]])])

(defn breadcrumb-component []
  [:div.row
   [:div.col-md-6
    [:ol.breadcrumb
     [shared/tree-objects-breadcrumb-component
      @tree-id*]
     [shared/project-configuration-breadcrumb-component
      @tree-id* :active? true]]]])

(defn title-component []
  [:div.title
   [:h1 " Project-Configuration for "
    [shared/tree-id-compact-component @tree-id* {:full false}]]
   [:p [shared/tree-id-compact-component @tree-id* {:full true}]]])

(defn page-component []
  [:div.project-configuration
   [breadcrumb-component]
   [title-component]
   [dependency-graph/visualization-component]
   [project-configuration-component]
   [debug-component]])

(defn page []
  (reagent/create-class
    {:reagent-render page-component
     }))
