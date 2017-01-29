(ns cider-ci.repository.ui.projects.edit-new.fetching
  (:require-macros
    [reagent.ratom :as ratom :refer [reaction]]
    )
  (:require
    [cider-ci.repository.constants :refer [CONTEXT]]
    [cider-ci.repository.ui.projects.edit-new.shared :refer [form-data dissected-git-url id project update-form-data update-form-data-value section]]
    [cider-ci.client.state :as state]
    [cider-ci.utils.core :refer [presence]]
    [cider-ci.utils.duration :as duration]
    ))

(def remote-fetch-interval-valid?
  (reaction (-> @form-data :remote_fetch_interval duration/valid?)))

(def remote-fetch-interval-input-state
  (reaction
    (cond
      (not @remote-fetch-interval-valid?) "has-error"
      :else "")))

(defn fetch-interval-input []
  (let [default-interval "1 Minute"]
    (fn []
      [:div.form-group {:class @remote-fetch-interval-input-state}
       [:label "Fetch interval"]
       [:input#remote_fetch_interval.form-control
        {:placeholder default-interval
         :on-change #(update-form-data-value :remote_fetch_interval (-> % .-target .-value presence))
         :value (-> @form-data :remote_fetch_interval)}]
       [:p.help-block "Cider-CI will actively fetch the Git URL after the last update,  "
        "and after the duration specified here has passed."
        [:span.text-warning
         " It can be problematic to set very low values, i.e. seconds,  here. "
         " Some providers will block your Cider-CI instance if it fetches too frequently! "]
        [:span.text-info
         " It is recommended to set up update notifications from your git provider
          to this project. Some providers call them \"webhooks\"."]]])))

(defn notification-token-input []
  [:div.form-group
   [:label "Notification token"]
   [:input#notification-token.form-control
    {:placeholder "Blank or valid UUID"
     :on-change #(update-form-data-value :update_notification_token (-> % .-target .-value presence))
     :value (-> @form-data :update_notification_token)}]
   [:p.help-block
    "This will be part of the URL to send update notifications from your git provider
    to this Cider-CI instance. "
    [:span.text-info "If you create a new project it is save and recommended to let this blank. A random token will be created in this case. "]
    [:span.text-warning "This value must be empty or a legal UUID."]]])

(defn component []
  [section "Git Fetch"
   (fn [] [:div
           [fetch-interval-input]
           [notification-token-input]])
   :description "These settings control pulling from the remove."])

