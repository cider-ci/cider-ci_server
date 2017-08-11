(ns cider-ci.server.trees.ui
  (:refer-clojure :exclude [str keyword])
  (:require-macros
    [reagent.ratom :as ratom :refer [reaction]]
    [cljs.core.async.macros :refer [go]]
    )
  (:require
    [cider-ci.server.client.request :as request]
    [cider-ci.server.client.routes :as routes]
    [cider-ci.server.client.state :as state]
    [cider-ci.server.trees.ui.shared :as shared]
    [cider-ci.server.ui2.shared :refer [pre-component]]
    [cider-ci.utils.core :refer [keyword str presence]]
    [cider-ci.utils.markdown :as markdown]
    [cider-ci.utils.sha1]

    [cljs.core.async :as async]
    [reagent.ratom :as ratom]
    [reagent.core :as reagent]
    ))

(def tree-id* (reaction (-> @state/page-state :current-page :tree-id)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn debug-component []
  (when (:debug @state/client-state)
    [:div.debug
     [:hr.clearfix]
     [:h2 "Page Debug"]
     [:div.tree-id
      [:h3 "@tree-id*"]
      (pre-component @tree-id*)]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn page-component []
  [:div.tree
   [:div.row
    [:div.col-md-6
     [:ol.breadcrumb
      [shared/tree-objects-breadcrumb-component @tree-id*
       :active? true]]]
    [:div.col-md-6
     [:ol.breadcrumb.with-circle-separator
      [shared/tree-objects-available-jobs-breadcrumb-component @tree-id* ]
      [shared/project-configuration-breadcrumb-component @tree-id*]
      ]]]
   [:h1 [:i.fa.fa-tree ] " " "Tree-Objects for "
    [shared/tree-id-compact-component @tree-id*]]
   [:p "Tree id: "
    [:span {:style {:font-family "monospace"}}
     @tree-id*]]
   [:h2 "Commits & Recursive Submodule Commits"]
   [:h2 "Jobs"]
   [debug-component]])


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn setup-page-data [_])

(defn clean-page-data [_])

(defn page []
  (reagent/create-class
    {:component-did-mount setup-page-data
     :component-will-unmount clean-page-data
     :reagent-render page-component
     }))
