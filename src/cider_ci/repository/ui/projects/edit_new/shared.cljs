(ns cider-ci.repository.ui.projects.edit-new.shared
  (:require-macros
    [reagent.ratom :as ratom :refer [reaction]]
    [cljs.core.async.macros :refer [go]]
    )
  (:require
    [cider-ci.utils.core :refer [presence]]
    [cider-ci.repository.constants :refer [CONTEXT]]
    [cider-ci.client.state :as state]
    ))


;### state ####################################################################

(def id (reaction (-> @state/page-state :current-page :id str presence)))

(def project (reaction (-> @state/server-state :repositories (get (keyword @id)))))

(def form-data (reaction (-> @state/client-state :edit-new-form-data)))

(def dissected-git-url
  (reaction
    (when-let[git-url (:git_url @form-data)]
      (cider-ci.utils.url/dissect git-url))))

(defn update-form-data [fun]
  (swap! state/client-state
         (fn [cs]
           (assoc cs :edit-new-form-data
                  (fun (:edit-new-form-data cs))))))

(defn update-form-data-value [k v]
  (update-form-data (fn [fd] (assoc fd k v))))


;### state ####################################################################

(defn section [title inputs & {:keys [description]} ]
  [:section
   [:hr]
   [:h3 title]
   (when (not (clojure.string/blank? description))
     [:p description])
   [inputs]])


