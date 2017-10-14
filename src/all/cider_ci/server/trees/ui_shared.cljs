(ns cider-ci.server.trees.ui-shared
  (:refer-clojure :exclude [str keyword])
  (:require-macros
    [reagent.ratom :as ratom :refer [reaction]]
    [cljs.core.async.macros :refer [go]]
    )
  (:require
    [cider-ci.server.client.connection.request :as request]
    [cider-ci.server.client.routes :as routes]
    [cider-ci.server.client.state :as state]
    [cider-ci.server.client.shared :refer [pre-component]]
    [cider-ci.utils.core :refer [keyword str presence]]
    [cider-ci.utils.markdown :as markdown]
    [cider-ci.utils.sha1]

    [cljs.core.async :as async]
    [reagent.core :as reagent]
    [reagent.ratom :as ratom]
    ))

;;; data ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def tree-id* (reaction (-> @state/page-state :current-page :tree-id)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn tree-id-compact-component
  [tree-id & [opts]]
  (let [full (:full opts)]
    [:span
     [:span
      [:i.fa.fa-tree ]]
     [:span {:style {:font-family "monospace"}}
      (if full
        (->> tree-id str (take 40) clojure.string/join)
        (->> tree-id str (take 6) clojure.string/join))]]))

(defn tree-objects-breadcrumb-component
  [tree-id & {:keys [active? show-tree-id?]
              :or {:active? false
                   :show-tree-id false}}]
  [:li {:key :tree-objects
        :class (when active? "active")}
   (let [internal [:span
                   [:i.fa.fa-tree]
                   " Tree-Objects "
                   (when show-tree-id?
                     [tree-id-compact-component tree-id])]]
     (if-not active?
       [:a {:href (routes/tree-path {:tree-id tree-id})}
        internal]
       internal))])

(defn tree-objects-available-jobs-breadcrumb-component
  [tree-id & {:keys [active? show-tree-id?]
              :or {:active? false
                   :show-tree-id false}}]
  [:li {:key :jobs
        :class (when active? "active")}
   (let [internal [:span
                   [:i.fa.fa-futbol-o]
                   " Run job "
                   (when show-tree-id?
                     [tree-id-compact-component tree-id])]]
     (if-not active?
       [:a {:href (routes/runnalbe-jobs-path {:tree-id tree-id})}
        internal]
       internal))])

(defn tree-objects-attachments-breadcrumb-component
  [tree-id & {:keys [active?]
              :or {:active? false
                   :show-tree-id false}}]
  [:li {:key :attachments
        :class (when active? "active")}
   (let [internal [:span
                   [:i.fa.fa-paperclip]
                   " Attachments " ]]
     (if-not active?
       [:a {:href (routes/tree-attachments-path {:tree-id tree-id})}
        internal]
       internal))])

(defn tree-objects-attachment-breadcrumb-component
  [tree-id path & {:keys [active?]
                   :or {:active? false}}]
  [:li {:key :attachment
        :class (when active? "active")}
   (let [internal [:span
                   [:i.fa.fa-paperclip]
                   " Attachment " ]]
     (if-not active?
       [:a {:href (routes/tree-attachment-path {:tree-id tree-id :path path})}
        internal]
       internal))])

(defn project-configuration-breadcrumb-component
  [tree-id & {:keys [active? show-tree-id?]
              :or {:active? false
                   :show-tree-id false}}]
  [:li {:key :project-configuration
        :class (when active? "active")}
   (let [internal [:span
                   [:i.fa.fa-code]
                   " Project-Configuration "
                   (when show-tree-id?
                     [tree-id-compact-component tree-id])]]
     (if-not active?
       [:a {:href (routes/project-configuration-path {:tree-id tree-id})}
        internal]
       internal))])

