(ns cider-ci.server.trees.dependency-graph
  (:refer-clojure :exclude [str keyword])
  (:require-macros
    [reagent.ratom :as ratom :refer [reaction]]
    [cljs.core.async.macros :refer [go]]
    )
  (:require
    [cider-ci.server.client.connection.request :as request]
    [cider-ci.server.client.routes :as routes]
    [cider-ci.server.client.shared :refer [pre-component]]
    [cider-ci.server.client.state :as state]
    [cider-ci.server.trees.ui-shared :refer [tree-id*]]
    [cider-ci.utils.core :refer [keyword str presence]]
    [cider-ci.utils.markdown :as markdown]
    [cider-ci.utils.sha1]

    [viz.core :as viz]
    [hickory.core :as hickory]
    [cljs-uuid-utils.core :as uuid]
    [cljs.core.async :as async]
    [clojure.walk :as walk]
    [reagent.core :as reagent]
    [reagent.ratom :as ratom]
    ))

(declare dependency-graph)

(def project-configurations* (ratom/atom {}))

(def project-configuration*
  (reaction (get @project-configurations* @tree-id* nil)))

(def dependency-and-trigger-graph*
  (reaction (dependency-graph @project-configuration*)))


;;; hiccup graph ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn normalize-hiccup-graph [hiccup-svg]
  (-> hiccup-svg
      first
      (#(walk/postwalk (fn [node]
                         (cond
                           (keyword? node) (case node
                                             :viewbox :viewBox
                                             :xmlns:xlink :xmlnsXlink
                                             node)
                           (map? node) (if-let [id (:id node)]
                                         (assoc node :key id)
                                         node)
                           :else node)) %))
      (assoc-in [1 :style] {:max-width "100%" :max-height "100%"})))


;;; loom graph ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn create-or-add-edge-label [graph arc d-or-t]
  (let [existing-label (presence (loom.attr/attr graph arc :label))
        add-label (str  (str (:type d-or-t) " " (:kind d-or-t) ":\n") (:key d-or-t))
        new-label (str (if existing-label (str existing-label "\n \n") "")
                       add-label)]
    (loom.attr/add-attr graph arc :label new-label)))

(defn add-edge [graph arc d-or-t]
  (-> graph
      (loom.graph/add-edges arc)
      (create-or-add-edge-label arc d-or-t)))

(defn add-node [graph node-key node-shape node-label]
  (-> graph
      (loom.graph/add-nodes node-key)
      (loom.attr/add-attr node-key :shape node-shape)
      (loom.attr/add-attr node-key :label node-label)
      (loom.attr/add-attr node-key :id (str node-key))))

(defn submodule-depenency-name-key [dependency]
  (->> (conj
         (or (:submodule dependency) [])
         (:job_key dependency))
       (clojure.string/join "/")
       keyword))

(defn add-dependency-or-trigger [graph job d-or-t]
  (as-> graph graph
    (case (:type d-or-t)
      "job" (let [source-key (submodule-depenency-name-key d-or-t)
                  target-key (-> job :key keyword)]
              (-> graph
                  (add-node source-key "box" (str source-key))
                  (add-node target-key "box" (str target-key))
                  (add-edge [source-key target-key] d-or-t)))
      "branch" (let [source-key (uuid/uuid-string (uuid/make-random-uuid))
                     target-key (-> job :key keyword)]
                 (-> graph
                     (add-node source-key "point" source-key)
                     (add-node target-key "box" target-key)
                     (add-edge [source-key target-key] d-or-t)))
      "cron" (let [source-key (uuid/uuid-string (uuid/make-random-uuid))
                   target-key (-> job :key keyword)
                   arc [source-key target-key]]
               (-> graph
                   (add-node source-key "point" source-key)
                   (add-node target-key "box" target-key)
                   (add-edge [source-key target-key] d-or-t))))))



;;; graph ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn add-job-to-graph [graph job]
  (as-> graph graph
    (reduce (fn [g d] (add-dependency-or-trigger g job (assoc d :kind "dependency")))
            graph
            (->> job :depends_on (map (fn [[k d]] (assoc d :key k)))))
    (reduce (fn [g t] (add-dependency-or-trigger g job (assoc t :kind "trigger")))
            graph
            (->> job :run_when (map (fn [[k t]] (assoc t :key k)))))))

(defn dependency-graph [project-configuration]
  (when project-configuration
    (let [jobs (->> project-configuration
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
       :hiccup-graph hiccup-graph})))


;;; setup page data ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn fetch-project-configuration []
  (let [id @tree-id*
        url (str "/cider-ci/trees/" id "/project-configuration" )
        resp-chan (async/chan)]
    (request/send-off {:url url :method :get}
                      {} :chan resp-chan)
    (go (let [resp (<! resp-chan)]
          (when (= (:status resp) 200)
            (swap! project-configurations*
                   assoc-in [@tree-id*] (->> resp :body)))))))

(defn setup-data [_]
  (fetch-project-configuration))

(defn visualization-component []
  (reagent/create-class
    {:component-will-mount setup-data
     :reagent-render (fn [_]
                       (if-let [hiccup-graph (:hiccup-graph @dependency-and-trigger-graph*)]
                         [:div.dependecy-visualization hiccup-graph]
                         [:div
                          [:alter.alert-info
                           "The dependency visualization is not available."]]))}))
