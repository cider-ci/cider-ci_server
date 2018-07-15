(ns cider-ci.server.trees.front-shared
  (:refer-clojure :exclude [str keyword])
  (:require-macros
    [reagent.ratom :as ratom :refer [reaction]]
    [cljs.core.async.macros :refer [go]]
    )
  (:require

    [cider-ci.server.front.requests.core :as requests]
    [cider-ci.server.front.state :as state]
    [cider-ci.server.paths :as paths :refer [path]]

    [cider-ci.utils.core :refer [keyword str presence]]
    [cider-ci.utils.markdown :as markdown]
    [cider-ci.utils.sha1]

    [cljs.core.async :as async]
    [reagent.core :as reagent]
    [reagent.ratom :as ratom]
    ))

;;; data ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def tree-id* (reaction (-> @state/routing-state* :route-params :tree-id)))


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
       [:a {:href (path :tree {:tree-id tree-id})}
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
       [:a {:href (path :tree-runnable-jobs {:tree-id tree-id})}
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
       [:a {:href (path :tree-attachments {:tree-id tree-id})}
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
       [:a {:href (path :tree-attachment {:tree-id tree-id :path path})}
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
       [:a {:href (path :tree-project-configuration {:tree-id tree-id})}
        internal]
       internal))])

