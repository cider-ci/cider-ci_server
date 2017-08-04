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
    [cider-ci.server.ui2.shared :refer [pre-component pre-component-fipp pre-component-pprint]]
    [cider-ci.utils.core :refer [keyword str presence]]
    [cider-ci.utils.markdown :as markdown]
    [cider-ci.utils.sha1]

    [cljs.pprint :refer [pprint]]
    [clojure.walk :as walk]
    [fipp.edn :refer [pprint] :rename {pprint fipp}]
    [hickory.core :as hickory]
    [loom.attr]
    [loom.graph]
    [loom.io]
    [viz.core :as viz]

    [cljs.core.async :as async]
    [reagent.ratom :as ratom]
    [reagent.core :as reagent]
    ))

(def tree-id* (reaction (-> @state/page-state :current-page :tree-id)))

(def dependency-and-trigger-graph* (ratom/atom nil))

(def project-configurations* (ratom/atom {}))

(def project-configuration* (ratom/atom (get @project-configurations* @tree-id* nil)))

;;; graph ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn normalize-hiccup-graph [hiccup-svg]
  (walk/postwalk (fn [node]
                   (cond
                     (keyword? node) (case node
                                       :viewbox :viewBox
                                       :xmlns:xlink :xmlnsXlink
                                       node)
                     (map? node) (if-let [id (:id node)]
                                   (assoc node :key id)
                                   node)
                     :else node))
                 hiccup-svg))

(defn submodule-depenency-name-key [dependency]
  (->>
    (conj
      (or (:submodule dependency) [])
      (:job_key dependency))
    (clojure.string/join "/")
    keyword))

(defn labe-prefix-for-kind [d-or-t]
  (case (:kind d-or-t)
    "trigger" "T"
    "dependency" "D"
    "UNSPECIFIED KIND"))

(defn add-dependency-or-trigger [graph job d-or-t]
  (js/console.log "ADD-DEPENDENCY-OR-TRIGGER")
  (js/console.log (with-out-str (pprint {:graph graph
                                         :job job
                                         :d-or-t d-or-t})))
  (as-> graph graph
    (case (:type d-or-t)
      "job" (let [source-key (submodule-depenency-name-key d-or-t)
                  target-key (-> job :key keyword)]
              (-> graph
                  (loom.graph/add-nodes target-key)
                  (loom.graph/add-nodes source-key)
                  (loom.attr/add-attr target-key :label (str target-key))
                  (loom.attr/add-attr target-key :shape "box")
                  (loom.attr/add-attr source-key :label (str source-key))
                  (loom.attr/add-attr source-key :shape "box")
                  (loom.graph/add-edges [source-key target-key])
                  (loom.attr/add-attr [source-key target-key]
                                      :label (str (labe-prefix-for-kind d-or-t) ": " (:key d-or-t)))))
      "branch" (let [source-key (-> d-or-t :key keyword)
                     target-key (-> job :key keyword)]
                 (-> graph
                     (loom.graph/add-nodes target-key)
                     (loom.graph/add-nodes source-key)
                     (loom.attr/add-attr target-key :label (str target-key))
                     (loom.attr/add-attr target-key :shape "box")
                     (loom.attr/add-attr source-key :id source-key)
                     (loom.attr/add-attr source-key :label "[B]")
                     (loom.attr/add-attr source-key :shape "circle")
                     (loom.graph/add-edges [source-key target-key])
                     (loom.attr/add-attr [source-key target-key]
                                         :label (str (labe-prefix-for-kind d-or-t) ": " (:key d-or-t)))))
      "cron" (let [source-key (-> d-or-t :key keyword)
                     target-key (-> job :key keyword)]
                 (-> graph
                     (loom.graph/add-nodes target-key)
                     (loom.graph/add-nodes source-key)
                     (loom.attr/add-attr target-key :label (str target-key))
                     (loom.attr/add-attr target-key :shape "box")
                     (loom.attr/add-attr source-key :id source-key)
                     (loom.attr/add-attr source-key :label "[C]")
                     (loom.attr/add-attr source-key :shape "circle")
                     (loom.graph/add-edges [source-key target-key])
                     (loom.attr/add-attr [source-key target-key]
                                         :label (str (labe-prefix-for-kind d-or-t) ": " (:key d-or-t)))))

      graph)))

(defn add-trigger [g job trigger]
  g)

(defn add-job-to-graph [graph job]
  ;(js/console.log (with-out-str (pprint ["ADD-JOB-TO-GRAPH" {:graph graph :job job}])))
  (as-> graph graph
    (reduce (fn [g d] (add-dependency-or-trigger g job (assoc d :kind "dependency")))
            graph
            (->> job :depends_on (map (fn [[k d]] (assoc d :key k)))))
    (reduce (fn [g t] (add-dependency-or-trigger g job (assoc t :kind "trigger")))
            graph
            (->> job :run_when (map (fn [[k t]] (assoc t :key k)))))))

(defn dependency-graph []
  (js/console.log "DEPENDENCY-GRAPH")
  (let [jobs (->> (get @project-configurations* @tree-id* nil)
                  :jobs
                  (map (fn [[k j]] (assoc j :key k))))
        loom-graph (reduce add-job-to-graph (loom.graph/digraph) jobs)
        gv-source-graph (loom.io/dot-str loom-graph)
        hiccup-graph (->> gv-source-graph
                          viz/image
                          hickory/parse-fragment
                          (map hickory/as-hiccup)
                          normalize-hiccup-graph)]
    {:loom-graph loom-graph
     :gv-source-graph gv-source-graph
     :hiccup-graph hiccup-graph}))

(defn setup-graph []
  (let [gv-graph (->> "digraph { FOO -> BAR; }" viz/image)
        hiccup-graph (->> gv-graph
                          hickory/parse-fragment
                          (map hickory/as-hiccup)
                          normalize-hiccup-graph)]
    (reset! dependency-and-trigger-graph* (dependency-graph))
    ))


;;; setup page data ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn fetch-project-configuration []
  (let [id @tree-id*
        url (str "/cider-ci/trees/" id "/project-configuration" )
        resp-chan (async/chan)]
    (request/send-off {:url url :method :get}
                      {:modal true} :chan resp-chan)
    (go (let [resp (<! resp-chan)]
          (when (= (:status resp) 200)
            (swap! project-configurations*
                   assoc-in [@tree-id*] (->> resp :body))
            (setup-graph))))))

(defn setup-page-data [_]
  ;(when-not @project-configuration*
  (fetch-project-configuration))


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
      [:h3 "@dependency-and-trigger-graph*"]
      [:pre (with-out-str (cljs.pprint/pprint @dependency-and-trigger-graph*))]]
     (when-let [gv-source-graph (-> @dependency-and-trigger-graph* :gv-source-graph presence)]
       [:div.gv-source-graph
        [:h3 "gv-source-graph"]
        [:pre gv-source-graph]])
     [:div.project-configurations
      [:h3 "@project-configurations*"]
      (pre-component-pprint @project-configurations*)]
     ]))

(defn project-configuration-component []
  [:div
   [:h2 "Normalized Project Configuration in JSON-Format"]
   (if-let [pc (get @project-configurations* @tree-id* nil)]
     (pre-component pc)
     [:alert.alert-warning
      [:p "There is currently no project configuration available for this tree-id. "
       "Check the requests and possibly reload the page manually. "]])])

(defn visualization-component []
  [:div
   [:h2 "Dependency and Trigger Visualization"]
   [:div (:hiccup-graph @dependency-and-trigger-graph*)]
   ])

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
   [visualization-component]
   [project-configuration-component]
   [debug-component]])

(defn page []
  (reagent/create-class
    {:component-did-mount setup-page-data
     :reagent-render page-component
     }))
