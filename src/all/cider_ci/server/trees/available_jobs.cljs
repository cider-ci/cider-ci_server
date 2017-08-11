(ns cider-ci.server.trees.ui.available-jobs
  (:refer-clojure :exclude [str keyword])
  (:require-macros
    [reagent.ratom :as ratom :refer [reaction]]
    [cljs.core.async.macros :refer [go]]
    )
  (:require
    [cider-ci.server.trees.ui.shared :as shared]
    [cider-ci.server.client.request :as request]
    [cider-ci.server.client.routes :as routes]
    [cider-ci.server.client.state :as state]
    [cider-ci.server.ui2.shared :refer [pre-component]]
    [cider-ci.utils.core :refer [keyword str presence]]
    [cider-ci.utils.markdown :as markdown]
    [cider-ci.utils.sha1]

    [cljs.core.async :as async]
    [reagent.ratom :as ratom]
    [reagent.core :as reagent]
    ))

(def tree-id* (reaction (-> @state/page-state :current-page :tree-id)))

(def available-jobs* (ratom/atom {}))

(defn fetch-available-jobs []
  (let [id @tree-id*
        url (str "/cider-ci/trees/" id "/available-jobs/" )
        resp-chan (async/chan)]
    (request/send-off {:url url :method :get}
                      {:modal true} :chan resp-chan)
    (go (let [resp (<! resp-chan)]
          (swap! available-jobs*
                 assoc-in [@tree-id*] (->> resp :body (sort-by :key)))))))

(defn setup-page-data [_]
  (fetch-available-jobs))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn submit-run-job [job-key]
  ;(js/console.log (clj->js job-key))
  (let [id @tree-id*
        url (str "/cider-ci/trees/" id "/jobs/" job-key)
        resp-chan (async/chan)]
    (request/send-off {:url url :method :post}
                      {:title (str "Create job `" job-key "`")
                       :modal true}
                      :chan resp-chan)
    (go (let [resp (<! resp-chan)]
          (js/console.log (clj->js resp))))))

(defn available-jobs-component []
  [:div
   (for [job (-> @available-jobs* (get @tree-id* []))]
     (let [runnable? (:runnable job)
           panel-class (if runnable? "panel-info" "panel-warning")]
       [:div.panel {:key (:key job)
                    :class panel-class}
        [:div.panel-heading
         [:a.btn.btn-primary.btn-xs
          (merge {:href (str "#" (:key job))
                  :on-click #(submit-run-job (:key job))}
                 (when-not runnable?
                   {:disabled true})) "Run"] " " (:name job) " "]
        (when-let [description (-> job :description presence)]
          [:div.panel-body
           {:dangerouslySetInnerHTML {:__html (markdown/md2html description)}}])
        (for [reason (:reasons job)]
          [:div.panel-footer
           {:key (cider-ci.utils.sha1/md5-hex (str (:key job) reason))
            :dangerouslySetInnerHTML {:__html (markdown/md2html reason)}}])]))])


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn debug-component []
  (when (:debug @state/client-state)
    [:div.debug
     [:hr.clearfix]
     [:h2 "Page Debug"]
     [:div.tree-id
      [:h3 "@tree-id*"]
      (pre-component @tree-id*)]
     [:div.available-jobs*
      [:h3 "@available-jobs*"]
      (pre-component @available-jobs*)]]))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn page-component []
  [:div.available-jobs
   [:div.row
    [:div.col-md-6
     [:ol.breadcrumb
      [shared/tree-objects-breadcrumb-component
       @tree-id*]
      [shared/tree-objects-available-jobs-breadcrumb-component
       @tree-id* :active? true] ]]]
   [:h1 [:i.fa.fa-futbol-o] " Available-Jobs for "
    [:span {:style {:font-family "monospace"}}
     (->> @tree-id* (take 6) clojure.string/join)]]
   [:p "Tree id: "
    [:span {:style {:font-family "monospace"}}
     @tree-id*]]
   [available-jobs-component]
   [debug-component]])

(defn page []
  (reagent/create-class
    {:component-did-mount setup-page-data
     :reagent-render page-component
     }))