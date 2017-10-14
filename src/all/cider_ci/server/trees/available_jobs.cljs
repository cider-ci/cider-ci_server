(ns cider-ci.server.trees.ui.available-jobs
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
    [cider-ci.server.trees.ui-shared :as shared]
    [cider-ci.utils.core :refer [keyword str presence]]
    [cider-ci.utils.markdown :as markdown]
    [cider-ci.utils.sha1]

    [accountant.core :as accountant]
    [cljs.core.async :as async]
    [reagent.core :as reagent]
    [reagent.ratom :as ratom]
    ))

(defn page-is-active? []
  (= (-> @state/page-state :current-page :component)
     "cider-ci.server.trees.ui.available-jobs/page"))

(def tree-id* (reaction (-> @state/page-state :current-page :tree-id)))

(def available-jobs* (ratom/atom {}))

(def fetch-available-jobs-id* (ratom/atom nil))

(defn fetch-available-jobs []
  (let [id @tree-id*
        url (str "/cider-ci/trees/" id "/available-jobs/" )
        resp-chan (async/chan)]
    (reset! fetch-available-jobs-id* id)
    (request/send-off {:url url :method :get}
                      {} :chan resp-chan)
    (go (let [resp (<! resp-chan)]
          (when (= (:status resp) 200)
            (swap! available-jobs*
                   assoc-in [@tree-id*] (->> resp :body (sort-by :key))))
          (js/setTimeout
            #(when (and (page-is-active?)
                        (= id @fetch-available-jobs-id*))
               (fetch-available-jobs))
            5000)))))

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
                       :retry #(submit-run-job job-key)}
                      :chan resp-chan)
    (go (let [resp (<! resp-chan)]
          ;(js/console.log (clj->js resp))
          (when (= (:status resp) 201)
            (accountant/navigate!
              (str "/cider-ci/ui/workspace/jobs/" (-> resp :body :id )))
            (js/location.reload))))))

(defn available-jobs-component []
  [:div
   (for [job (-> @available-jobs* (get @tree-id* []))]
     (let [runnable? (:runnable job)
           panel-class (if runnable? "panel-info" "panel-warning")
           job-class (if runnable? "runnable-job" "unrunnalbe-job") ]
       [:div.panel {:key (:key job)
                    :data-name (:name job)
                    :data-key (:key job)
                    :class (str panel-class " " job-class)}
        [:div.panel-heading
         [:button.btn.btn-primary.btn-xs
          (merge {:on-click #(submit-run-job (:key job))}
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

(defn breadcrumb-component []
  (breadcrumbs/breadcrumb-component
    [(breadcrumbs/home-li-component)
     (shared/tree-objects-breadcrumb-component @tree-id*)
     (shared/tree-objects-available-jobs-breadcrumb-component @tree-id* :active? true)]
    []))

(defn page-component []
  [:div.available-jobs
   [breadcrumb-component]
   [:h1 " Run job for "
    [:i.fa.fa-tree]
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
