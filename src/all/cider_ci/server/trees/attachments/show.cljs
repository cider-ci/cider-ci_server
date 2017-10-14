; Copyright © 2013 - 2017 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.server.trees.attachments.show
  (:refer-clojure :exclude [str keyword])
  (:require-macros
    [reagent.ratom :as ratom :refer [reaction]]
    [cljs.core.async.macros :refer [go]]
    )
  (:require
    [cider-ci.server.client.breadcrumbs :as breadcrumbs]
    [cider-ci.server.client.connection.request :as request]
    [cider-ci.server.client.routes :as routes]
    [cider-ci.server.client.shared :refer [pre-component]]
    [cider-ci.server.client.state :as state]
    [cider-ci.server.client.utils :refer [humanize-datetime-component]]
    [cider-ci.server.trees.ui-shared :as shared :refer [tree-id*]]
    [cider-ci.utils.core :refer [keyword str presence]]

    [cljs.core.async :as async]
    [clojure.contrib.inflect :refer [pluralize-noun]]
    [clojure.pprint :refer [pprint]]
    [reagent.core :as reagent]
    [reagent.ratom :as ratom]

    ))


;;; components

(defn title-component []
  [:div.title
   [:h1
    [:i.fa.fa-paperclip]
    " Tree-Attachment "
    [:span {:style {:font-family "monospace"}}
     (-> @state/page-state :current-page :path)
     ]
    " for "
    [shared/tree-id-compact-component @tree-id* {:full false}]]
   [:p [shared/tree-id-compact-component @tree-id* {:full true}]]])

(defn attachment-component []
  [:div {:style {:display :flex}}
   [:iframe
    {:style {:width "100%"
             :height "600px"}
     :src (str "/cider-ci/storage/tree-attachments/"
               @tree-id* "/"  (-> @state/page-state :current-page :path js/encodeURIComponent))}
    ]])


(defn breadcrumb-component []
  (breadcrumbs/breadcrumb-component
    [(breadcrumbs/home-li-component)
     (shared/tree-objects-breadcrumb-component @tree-id*)
     (shared/tree-objects-attachments-breadcrumb-component @tree-id*)
     (shared/tree-objects-attachment-breadcrumb-component
       @tree-id* (-> @state/page-state :current-page :path) :active? true)]
    []))

(defn page-component []
  [:div.tree-attachments
   [breadcrumb-component]
   [title-component]
   [attachment-component]
   ])

(defn page []
  (reagent/create-class
    {:reagent-render page-component
     }))


