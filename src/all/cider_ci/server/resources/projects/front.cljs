; Copyright © 2013 - 2018 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.server.resources.projects.front
  (:refer-clojure :exclude [str keyword])
  (:require-macros
    [reagent.ratom :as ratom :refer [reaction]]
    [cljs.core.async.macros :refer [go]])
  (:require
    [cider-ci.server.resources.api-token.front :as api-token]
    [cider-ci.server.front.requests.core :as requests]
    [cider-ci.server.front.breadcrumbs :as breadcrumbs]
    [cider-ci.server.front.state :as state :refer [routing-state*]]
    [cider-ci.server.paths :as paths :refer [path]]
    [cider-ci.server.resources.auth.front :as auth]
    [cider-ci.server.front.shared :as shared :refer [humanize-datetime-component name->key]]
    [cider-ci.server.resources.projects.shared :refer [default-project-params]]

    [cider-ci.utils.core :refer [keyword str presence]]
    [cider-ci.utils.seq :refer [with-index]]

    [accountant.core :as accountant]
    [cljs.pprint :refer [pprint]]
    [cljs.core.async :as async]
    [cljs.core.async :refer [timeout]]
    [reagent.core :as reagent]

    ))


(defonce projects-data* (reagent/atom nil))
(defonce project-data* (reagent/atom default-project-params))
(defonce project-id* (reaction (-> @state/routing-state* :route-params :project-id)))
(defonce handler-key* (reaction (-> @state/routing-state* :handler-key)))



(defn clean-data [& args] 
  (reset! projects-data* nil) 
  (reset! project-data* default-project-params))

(declare post-add patch)

;##############################################################################
;### fetch ####################################################################
;##############################################################################

(def fetch-project-id* (reagent/atom nil))
(defn fetch-project [& args]
  (let [resp-chan (async/chan)
        id (requests/send-off {:url (path :project {:project-id @project-id*})
                               :method :get}
                              {:modal false
                               :title "Fetch project"
                               :retry-fn #'fetch-project}
                              :chan resp-chan)]
    (reset! fetch-project-id* id)
    (go (let [resp (<! resp-chan)]
          (when (and (= (:status resp) 200) ;success
                     (= id @fetch-project-id*) ;still the most recent request
                     (reset! project-data* (->> resp :body))))))))


(def fetch-projects-id* (reagent/atom nil))
(defn fetch-projects [& args]
  (let [resp-chan (async/chan)
        id (requests/send-off {:url (path :projects)
                               :method :get}
                              {:modal false
                               :title "Fetch projects"
                               :retry-fn #'fetch-projects}
                              :chan resp-chan)]
    (reset! fetch-projects-id* id)
    (go (let [resp (<! resp-chan)]
          (when (and (= (:status resp) 200) ;success
                     (= id @fetch-projects-id*) ;still the most recent request
                     (reset! projects-data* 
                             (update-in (->> resp :body) [:projects] 
                                        (partial with-index 0)))))))))


;##############################################################################
;### helpers ##################################################################
;##############################################################################


(defn refresh-project-data [& args]
  (clean-data)
  (fetch-project))

(defn title-name-component []
  (when-let [name (-> @project-data* :name presence)]
    [:span " \"" name "\" "]))


;##############################################################################
;### form #####################################################################
;##############################################################################

(defonce form-disabled* (reaction (case @handler-key*
                                    :projects-add false
                                    :project-edit false
                                    true)))

(defn checkbox-input-component [id ratom keyseq]
  [:input
   {:id id
    :type :checkbox
    :checked (get-in @ratom keyseq false)
    :on-change #(swap! ratom (fn [d]
                               (assoc-in d keyseq
                                         (not (get-in d keyseq false)))))
    :disabled @form-disabled* }])

(defn form-branch-trigger-enabled-component []
  [:div.checkbox
   [:label
    [checkbox-input-component :branch_trigger_enabled
     project-data* [:branch_trigger_enabled]]
    " Branch trigger enabled "]])



(defn form-name-component []
  [:div.form-group
   [:label  {:for :name} "Name"]
   [:input#name.form-control
    {:type :text
     :value (-> @project-data* :name)
     :on-change (fn [e] 
                  (let [value (-> e .-target .-value)]
                    (swap! project-data* 
                           (fn [d]
                             (assoc d
                                    :name value
                                    :id (if (= :projects-add (:handler-key @state/routing-state*))
                                           (name->key value)
                                           (:id d)))))))
     :disabled @form-disabled* }]])

(defn form-id-component []
  [:div.form-group
   [:label  {:for :id} "Id"]
   [:input#id.form-control
    {:type :text
     :value (-> @project-data* :id)
     :on-change #(swap! project-data* assoc :id (-> % .-target .-value))
     :disabled (if (= :projects-add (:handler-key @state/routing-state*))
                 false true)}]
   [:small.form-text 
    "The id of a project is used in URLs and and in other places to reference a project. " 
    "The id must begin with an lower case letter. "
    "Further only lower case letters, numbers, dashes and underscores are allowed. "
    "The id can not be changed after the project has been added. " ]])

(defn form-public-permission-component []
  [:div.checkbox
   [:label
    [checkbox-input-component :public_view_permission 
     project-data* [:public_view_permission]]
    " Public view permission "]
   [:small.form-text
    " If this is checked the status of jobs, tasks, and trials belonging to this project can be viewed without being singed in. "
    " Likewise read access via git as well as download of attachments is possible without authentication. " ]])

(defn form-cron-trigger-enabled-component []
  [:div.checkbox
   [:label
    [checkbox-input-component :cron_trigger_enabled
     project-data* [:cron_trigger_enabled]]
    " Cron trigger enabled "]])

(defn form-component []
  [:form.form
   {:on-submit (fn [e]
                 (.preventDefault e)
                 (case (:handler-key @state/routing-state*)
                   :projects-add (post-add)
                   :project-edit (patch)))}
   [form-name-component]
   [form-id-component]
   [form-public-permission-component]

   [:h3.mt-3 "Triggers"]
   [form-branch-trigger-enabled-component]
   [form-cron-trigger-enabled-component]

   (case (:handler-key @state/routing-state*)
     :projects-add [:button.btn.btn-primary.float-right
                    {:type :submit}
                    "Add project"]
     :project-edit [:button.btn.btn-warning.float-right
                    {:type :submit}
                    "Save"]
     [:div])
   [:div.clearfix]])


;##############################################################################
;### add ######################################################################
;##############################################################################

(defn post-add []
  (let [resp-chan (async/chan)
        id (requests/send-off {:url (path :projects-add)
                               :method :post
                               :json-params  @project-data*}
                              {:modal true
                               :title "Add project"
                               :handler-key :projects-add
                               :retry-fn post-add}
                              :chan resp-chan)]
    (go (let [resp (<! resp-chan)]
          (when (< (:status resp) 400)
            (accountant/navigate! (path :project {:project-id (-> resp :body :id)})))))))

(defn refresh-add-data [& args]
  (reset! project-data* default-project-params))

(defn add-debug-component []
  (when (:debug @state/global-state*)
    [:div
     [:pre (with-out-str (pprint @project-data*))]]))

(defn add-page []
  [:div
   [state/hidden-routing-state-component
    {:will-mount refresh-add-data 
     :did-change refresh-add-data}]
   (breadcrumbs/nav-component
     [(breadcrumbs/home-li)
      (breadcrumbs/projects-li)
      (breadcrumbs/projects-add-li)][])
   [:div
    [:h1 "Add a Project"]
    [form-component]
    [add-debug-component]
    ]])


;##############################################################################
;### delete ###################################################################
;##############################################################################

(defn delete []
  (let [resp-chan (async/chan)
        id (requests/send-off {:url (path :project {:project-id @project-id*})
                               :method :delete}
                              {:modal true
                               :title "Delete project"
                               :retry-fn delete}
                              :chan resp-chan)]
    (go (let [resp (<! resp-chan)]
          (when (#{201 204} (:status resp))
            (accountant/navigate! (path :projects)))))))

(defn delete-page []
  [:div
   [state/hidden-routing-state-component
    {:will-mount refresh-project-data 
     :did-change refresh-project-data}]
   (breadcrumbs/nav-component
     [(breadcrumbs/home-li)
      (breadcrumbs/projects-li)
      (breadcrumbs/project-li @project-id*)
      (breadcrumbs/project-delete-li @project-id*)][])
   [:div
    [:h1 "Delete project " [title-name-component]]
    [:p [:span.text-warning "Deleting a project will also delete associated entities (pull and push). "]
     " Jobs are only loosly coupled to projects via the tree-id and will persist. "]
    [:form.form.clearfix
     {:on-submit (fn [e]
                   (.preventDefault e)
                   (delete))}

     [:button.btn.btn-danger.float-right
      {:type :submit}
      "Delete"]]]])


;##############################################################################
;### edit #####################################################################
;##############################################################################

(defn patch []
  (let [resp-chan (async/chan)
        id (requests/send-off {:url (path :project {:project-id @project-id*})
                               :method :patch
                               :json-params @project-data*}
                              {:modal true
                               :title "Update project"
                               :retry-fn patch}
                              :chan resp-chan)]
    (go (let [resp (<! resp-chan)]
          (when (#{201 204} (:status resp))
            (accountant/navigate! (path :project {:project-id @project-id*})))))))

(defn edit-page []
  [:div
   [state/hidden-routing-state-component
    {:will-mount refresh-project-data
     :did-change refresh-project-data}]
   (breadcrumbs/nav-component
     [(breadcrumbs/home-li)
      (breadcrumbs/projects-li)
      (breadcrumbs/project-li @project-id*)
      (breadcrumbs/project-edit-li @project-id*)][])
   [:div
    [:h1 "Edit project " [title-name-component]]
    (if (= @project-data* default-project-params)
      [shared/please-wait-component]
      [:div [form-component]])]])


;##############################################################################
;### show #####################################################################
;##############################################################################

(defn show-page []
  [:div
   [state/hidden-routing-state-component
    {:will-mount (fn [_] (clean-data) (fetch-project))
     :did-change (fn [_] (clean-data) (fetch-project))
     }]
   (breadcrumbs/nav-component
     [(breadcrumbs/home-li)
      (breadcrumbs/projects-li)
      (breadcrumbs/project-li @project-id*)]
     [(breadcrumbs/project-delete-li @project-id*)
      (breadcrumbs/project-edit-li @project-id*)])
   [:div
    [:h1 " Project " [title-name-component]]
    (if (= @project-data* default-project-params)
      [shared/please-wait-component]
      [form-component])]])


;##############################################################################
;### index ####################################################################
;##############################################################################

(defn project-link [project inner]
  [:a {:href (path :project {:project-id (:id project)})}
   inner])

(defn index-table-component []
  (if-let [projects-data (:projects @projects-data*)]
    [:table.table.table-striped.table-sm
     [:thead
      [:tr 
       [:th "Index"]
       [:th "Name"]
       [:th "Added"]]]
     [:tbody
      (doall (for [project-data projects-data]
               [:tr {:id (:id project-data)}
                [:td (project-link project-data (:index project-data))]
                [:td (project-link project-data (:name project-data))]
                [:td {:id :added_at} (-> project-data :created_at humanize-datetime-component)]
                ]))]]
    [shared/please-wait-component]))

(defn index-debug-component []
  (when (:debug @state/global-state*)
    [:section.debug
     [:hr]
     [:h2 "Page Debug"]
     [:div.users
      [:h3 "@projects-data*"]
      [:pre (with-out-str (pprint @projects-data*))]]]))

(defn refresh-index-data [& args]
  (fetch-projects))

(defn index-page []
  [:div
   [state/hidden-routing-state-component
    {:will-mount refresh-index-data
     :did-change refresh-index-data}]
   (breadcrumbs/nav-component
     [(breadcrumbs/home-li)
      (breadcrumbs/projects-li)]
     [(breadcrumbs/projects-add-li)])
   [:div
    [:h1 " Projects "]
    [index-table-component]
    [index-debug-component]
    ]])
