(ns cider-ci.server.trees.ui.project-configuration
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

(def project-configurations* (ratom/atom {}))

(defn fetch-project-configuration []
  (let [id @tree-id*
        url (str "/cider-ci/trees/" id "/project-configuration" )
        resp-chan (async/chan)]
    (request/send-off {:url url :method :get}
                      {:modal true} :chan resp-chan)
    (go (let [resp (<! resp-chan)]
          (when (= (:status resp) 200)
            (swap! project-configurations*
                   assoc-in [@tree-id*] (->> resp :body)))))))

(defn setup-page-data [_]
  (when-not (get @project-configurations* @tree-id* nil)
    (fetch-project-configuration)))


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
      [:h3 "@project-configurations*"]
      (pre-component @project-configurations*)]]))

(defn project-configuration-component []
  (if-let [pc (get @project-configurations* @tree-id* nil)]
    (pre-component pc)
    [:alert.alert-warning
     [:p "There is currently no project configuration available for this tree-id. "
      "Check the requests and possibly reload the page manually. "]]))

(defn page-component []
  [:div.project-configuration
   [:div.row
    [:div.col-md-6
     [:ol.breadcrumb
      [shared/tree-objects-breadcrumb-component
       @tree-id*]
      [shared/project-configuration-breadcrumb-component
       @tree-id* :active? true] ]]]
   [:h1 [:i.fa.fa-code] " Project-Configuration for "
    [:span {:style {:font-family "monospaced"}}
     (->> @tree-id* (take 6) clojure.string/join)]]
   [:p "Tree id: "
    [:span {:style {:font-family "monospaced"}} @tree-id*]]
   [project-configuration-component]
   [debug-component]])

(defn page []
  (reagent/create-class
    {:component-did-mount setup-page-data
     :reagent-render page-component
     }))
