; Copyright © 2013 - 2017 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.server.trees.attachments.ui
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

(def fetch-id*
  "An id to keep track of the most recent and thus (maybe) not outdated request."
  (ratom/atom nil))

(def attachments* (ratom/atom {}))

(defn fetch []
  (let [query-params {}
        path (str "/cider-ci/tree-attachments/" @tree-id* "/")
        resp-chan (async/chan)
        id (request/send-off {:url path :method :get :query-params query-params}
                             {:modal false
                              :title "Fetch Tree-Attachments"}
                             :chan resp-chan)]
    (reset! fetch-id* id)
    (go (let [resp (<! resp-chan)]
          (when (and (= (:status resp) 200) ;success
                     (= id @fetch-id*)) ;still the most recent request
            (swap! attachments* assoc-in [@tree-id*] (:body resp)))
          (js/setTimeout
            #(when (= id @fetch-id*) (fetch))
            (* 60 1000))))))

;;; server-entity-event-update-receiver ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn server-entity-event-receiver [event]
  (case (:table_name event)
    "tree_attachments" (when (or (= (-> event :data_new :tree_id) @tree-id*)
                                 (= (-> event :data_old :tree_id) @tree-id*))
                         (fetch))
    nil))


;;; components

(defn title-component []
  [:div.title
   [:h1
    [:i.fa.fa-paperclip]
    " Tree-Attachments for "
    [shared/tree-id-compact-component @tree-id* {:full false}]]
   [:p [shared/tree-id-compact-component @tree-id* {:full true}]]])

(defn attachment-row-component [attachment]
  [:tr {:key (:id attachment) :id (:id attachment) :data attachment}
   [:td {:key :iframe}
    [:a {:href (routes/tree-attachment-path
                 {:tree-id @tree-id*
                  :path (-> attachment :path js/encodeURIComponent)})}
     [:i.fa.fa-image] " Show " ]]
   [:td {:key :download}
    [:a {:download true
         :href (str "/cider-ci/storage/tree-attachments/"
                    @tree-id* "/" (-> attachment :path js/encodeURIComponent))}
     [:i.fa.fa-download] " Download "]]
   [:td {:key :content_length}
    (clojure.contrib.humanize/filesize
      (:content_length attachment))]
   [:td {:key :content_type} (:content_type attachment)]
   [:td {:key :path} (:path attachment)]
   [:td {:key :created_at}
    (-> attachment :created_at humanize-datetime-component)]])

(defn attachments-component []
  (if-not (-> @attachments* (contains? @tree-id*))
    [:div
     [:div.jumbotron.text-center [:i.fa.fa-spinner.fa-spin.fa-3x.fa-fw]]]
    [:div
     (let [attachments (get @attachments* @tree-id* [])
           n (count attachments)]
       [:div
        [:h2 n " " (pluralize-noun n "Attachment")]
        [:table.attachments.table.table-striped
         [:thead]
         [:tbody
          (doall (for [attachment attachments]
                   (attachment-row-component attachment)))
          ]]])]))

(defn debug-component []
  [:div.page-debug
   [:h2 "Page-Debug"]
   [:div
    [:h3 "@attachments*"]
    [:pre
     (with-out-str (pprint @attachments*))
     ]
    ]])

(defn breadcrumb-component []
  (breadcrumbs/breadcrumb-component
    [(breadcrumbs/home-li-component)
     (shared/tree-objects-breadcrumb-component @tree-id*)
     (shared/tree-objects-attachments-breadcrumb-component @tree-id* :active? true)]
    []))

(defn page-component []
  [:div.tree-attachments
   [breadcrumb-component]
   [title-component]
   [attachments-component]
   [debug-component]
   ])

(defn post-mount-setup [component]
  (swap! state/page-state assoc
         :server-entity-event-receiver server-entity-event-receiver)
  (fetch))

(defn page []
  (reagent/create-class
    {:reagent-render page-component
     :component-did-mount post-mount-setup
     }))


