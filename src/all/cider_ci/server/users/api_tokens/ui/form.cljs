(ns cider-ci.server.users.api-tokens.ui.form
  (:refer-clojure :exclude [str keyword])
  (:require-macros
    [reagent.ratom :as ratom :refer [reaction]]
    )
  (:require
    [cider-ci.server.client.state :as state]
    [cider-ci.server.client.utils :refer [humanize-datetime-component]]
    [cider-ci.server.users.api-tokens.ui.shared :refer [scopes]]
    [cider-ci.utils.core :refer [keyword str presence]]
    [clojure.string :refer [join split]]
    ))

(defn valid-iso8601? [iso]
  (.isValid (js/moment iso)))

(def form-data* (reaction (-> @state/client-state :api-token :form)))

(def description-valid*? (reaction (-> @form-data* :description presence boolean)))

(def expires-at-valid*? (reaction (-> @form-data* :expires_at valid-iso8601?)))

(def form-valid*? (reaction (and @description-valid*?
                                 expires-at-valid*?)))

(defn reset-api-token-form-data []
  (swap! state/client-state
         assoc-in [:api-token :form]
         {:description nil
          :scope_read true
          :scope_write false
          :scope_admin_read false
          :scope_admin_write false
          :expires_at (.format (.add (js/moment) 1, "year"))
          }))

(defn update-form-data [fun]
  (swap! state/client-state
         (fn [cs]
           (assoc-in cs [:api-token :form]
                     (fun (-> cs :api-token :form))))))

(defn update-form-data-value [k v]
  (update-form-data (fn [fd] (assoc fd k v))))

;;; scopes ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def scope-presets
  {:user_read {:scope_read true :scope_write false
               :scope_admin_read false :scope_admin_write false}
   :user_write {:scope_read true :scope_write true
                :scope_admin_read false :scope_admin_write false}
   :admin {:scope_read true :scope_write true
           :scope_admin_read true :scope_admin_write true}})

(defn scope-disalbed? [scope]
  (case scope
    :scope_read (or (-> @form-data* :scope_admin_read)
                    (-> @form-data* :scope_write))
    :scope_write (or (-> @form-data* :scope_read not)
                     (-> @form-data* :scope_admin_write))
    :scope_admin_read (or (-> @form-data* :scope_read not)
                          (-> @form-data* :scope_admin_write))
    :scope_admin_write (or (-> @form-data* :scope_admin_read not)
                           (-> @form-data* :scope_write not))))

(defn scope-form-component [scope]
  [:div.checkbox {:key scope}
   [:label
    [:input {:id (str scope)
             :type :checkbox
             :disabled (scope-disalbed? scope)
             :checked (-> @form-data* scope)
             :on-change #(update-form-data (fn [fd] (assoc fd scope (-> fd scope not))))}]
    (->> (-> scope str (split "_")) (drop 1) (join " "))]])

(defn scope-presets-component []
  [:p.scope-presets.help-block "Presets: "
   (for [[pk pv] scope-presets]
     [:span
      [:button.btn.btn-xs.btn-default
       {:on-click #(update-form-data (fn [fd] (merge fd pv)))}
       (->> (-> pk str (split "_")) (join " "))] " "])])

(defn scopes-form-component []
  [:div.form-group
   [:label "Scopes:" ]
   [:p.help-block
    "Read and write correspond to perform actions
    via safe (read) or unsafe (write) HTTP verbs." ]
   [scope-presets-component]
   (doall (for [scope scopes] [scope-form-component scope]))
   [:p.help-block
    "Enabled admin scopes will have effect if and only if the corresponding
    user has admin privileges at the time this tokes is used."]
   ])

;;; description ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn description-form-component []
  [:div.form-group {:class (when (not @description-valid*?) "has-error")}
   [:label "Description:" ]
   [:input#description.form-control
    {:on-change #(update-form-data-value :description (-> % .-target .-value presence))
     :value (-> @form-data* :description)}]
   [:p.help-block
    "The description may not be empty!" ]])

;;; expires ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn expires-presets-component []
  [:p.help-bock "Presets: "
   (for [period ["day" "week" "month" "year"]]
     [:span {:key period} " "
      [:button.btn.btn-xs.btn-default
       {:id period
        :on-click #(update-form-data-value
                     :expires_at (->  (.add (js/moment) 1, period) .format))}
       "now + 1 " period]])
   [:span {:key :never} " "
    [:button.btn.btn-xs.btn-default
     {:id :never
      :on-click #(update-form-data-value
                   :expires_at (->  (.add (js/moment) 1000, "years") .format))}
     "never"]]])

(defn expires-at-form-component []
  [:div.form-group {:class (when (not @expires-at-valid*?) "has-error")}
   [:label "Expires" ":" ]
   [expires-presets-component]
   [:input#expires_at.form-control
    {:type :datetime-local
     :on-change #(when-let [iso (-> % .-target .-value presence
                                    (js/moment "YYYY-MM-DDTHH:mm:ss") .format)]
                   (update-form-data-value :expires_at iso))
     :value (-> @form-data* :expires_at
                js/moment .local (.format "YYYY-MM-DDTHH:mm:ss"))}]
   [:p.help-block
    (when @expires-at-valid*?
      [:span "This API-token will expire "
       (humanize-datetime-component (-> @form-data* :expires_at js/moment))
       ". "])]])

;;; form ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn form-component []
  [:div.form
   [description-form-component]
   [scopes-form-component]
   [expires-at-form-component]
   ])
