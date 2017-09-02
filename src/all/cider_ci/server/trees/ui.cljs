(ns cider-ci.server.trees.ui
  (:refer-clojure :exclude [str keyword])
  (:require-macros
    [reagent.ratom :as ratom :refer [reaction]]
    [cljs.core.async.macros :refer [go]]
    )
  (:require
    [cider-ci.constants :as constants :refer [GRID-COLS]]
    [cider-ci.server.client.request :as request]
    [cider-ci.server.client.routes :as routes]
    [cider-ci.server.client.state :as state]
    [cider-ci.server.client.utils :refer [humanize-datetime-component]]
    [cider-ci.server.trees.ui.shared :as shared]
    [cider-ci.server.ui2.shared :refer [pre-component]]
    [cider-ci.shared.icons :as icons]
    [cider-ci.utils.core :refer [keyword str presence]]
    [cider-ci.utils.markdown :as markdown]
    [cider-ci.utils.sha1]

    [cljs.core.async :as async]
    [reagent.ratom :as ratom]
    [reagent.core :as reagent]
    ))

(def tree-id* (reaction (-> @state/page-state :current-page :tree-id)))

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
            (reset! jobs* (:body resp)))
          (js/setTimeout
            #(when (and (page-is-active?)
                        (= id @fetch-jobs-id*))
               (fetch-jobs))
            5000)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

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
      (pre-component @jobs*)]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn page-component []
  [:div.tree
   [:div.row
    [:div
     {:class (str "col-md-" (Math/floor (/ GRID-COLS 2)))}
     [:ol.breadcrumb
      [shared/tree-objects-breadcrumb-component @tree-id*
       :active? true]]]
    [:div
     {:class (str "col-md-" (Math/floor (/ GRID-COLS 2)))}
     [:ol.breadcrumb.with-circle-separator
      [shared/tree-objects-available-jobs-breadcrumb-component @tree-id* ]
      [shared/project-configuration-breadcrumb-component @tree-id*]
      ]]]
   [:h1  "Jobs and Tree-Objects for " [:i.fa.fa-tree ]
    [shared/tree-id-compact-component @tree-id*]]
   [:p "Tree id: "
    [:span {:style {:font-family "monospace"}}
     @tree-id*]]
   [:section.jobs
    [:h2 "Jobs"]
    [:table.jobs-table.table
     [:thead]
     [:tbody
      (doall
        (for [[_ job] @jobs*]
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
           ]))]]]
   [debug-component]])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn setup-page-data [_]
  (fetch-jobs))

(defn clean-page-data [_]
  (reset! jobs* {})
  )

(defn page []
  (reagent/create-class
    {:component-did-mount setup-page-data
     :component-will-unmount clean-page-data
     :reagent-render page-component
     }))
