(ns cider-ci.server.executors.ui.form
  (:refer-clojure :exclude [str keyword])
  (:require-macros
    [reagent.ratom :as ratom :refer [reaction]]
    )
  (:require
    [cider-ci.server.client.state :as state]
    [cider-ci.server.client.utils :refer [humanize-datetime-component]]
    [cider-ci.utils.core :refer [keyword str presence]]
    [clojure.string :refer [join split]]
    ))

(def form-data* (reaction (-> @state/client-state :executor :form)))

(def name-valid*?
  (reaction
    (and
      (-> @form-data* :name presence boolean)
      (re-matches #"[\w-_\.:]+" (-> @form-data* :name)))))

(def token-valid*?
  (reaction
    (let [token (-> @form-data* :token presence)]
      (or (-> token boolean not)
          (and (<= 16 (count token) 64)
               (re-matches #"\w+=*" token))))))

(def form-valid*? (reaction @name-valid*?))

(def form-defaults
  {:name nil
   :description nil
   :token nil
   :enabled true
   :upload_tree_attachments true
   :upload_trial_attachments true})

(defn reset-executor-form-data
  ([] (reset-executor-form-data {}))
  ([params]
   (swap! state/client-state
          assoc-in [:executor :form]
          (merge form-defaults params))))

(defn update-form-data [fun]
  (swap! state/client-state
         (fn [cs]
           (assoc-in cs [:executor :form]
                     (fun (-> cs :executor :form))))))

(defn update-form-data-value [k v]
  (update-form-data (fn [fd] (assoc fd k v))))


;;; form ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;; permissions ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn scope-form-component [scope]
  [:div.checkbox {:key scope}
   [:label
    [:input {:id (str scope)
             :type :checkbox
             :checked (-> @form-data* scope)
             :on-change #(update-form-data (fn [fd] (assoc fd scope (-> fd scope not))))}]
    (->> (-> scope str (split "_")) (join " "))]])


(defn scopes-form-component []
  [:div.form-group
   [:label "Permissions" ]
   (doall (for [scope [:enabled :upload_tree_attachments :upload_trial_attachments]]
            [scope-form-component scope]))
   [:p.help-block ]
   ])

;;; name and description ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn name-form-component []
  [:div.form-group {:class (when (not @name-valid*?) "has-error")}
   [:label "Name:" ]
   [:input#name.form-control
    {:on-change #(update-form-data-value :name (-> % .-target .-value presence))
     :value (-> @form-data* :name)}]
   [:p.help-block ]])

(defn description-form-component []
  [:div.form-group
   [:label "Description:" ]
   [:input#description.form-control
    {:on-change #(update-form-data-value :description (-> % .-target .-value presence))
     :value (-> @form-data* :description)}]])

;;; token ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn token-component []
  [:div.form-group {:class (when (not @token-valid*?) "has-error")}
   [:label "Token:" ]
   [:p.help-block
    "The token is a secret used to authorize the executor against your Cider-CI server. "]
   [:input#token.form-control
    {:on-change #(update-form-data-value :token (-> % .-target .-value presence))
     :value (-> @form-data* :token)
     :placeholder (if (-> @form-data* :token_part presence)
                    "**************************"
                    "leave empty to use a generated token (recommended!)")}]
   [:p.help-block
    "Every token must be unique within a Cider-CI instance. It must consist of
    alphanumeric characters with optional \"=\" padding.
    It must be at least 16 and at most 64 characters long."]
   ])



(defn form-component []
  [:div.form
   [name-form-component]
   [description-form-component]
   [token-component]
   [scopes-form-component]])
