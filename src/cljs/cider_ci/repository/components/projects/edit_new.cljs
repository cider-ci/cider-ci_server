(ns cider-ci.repository.components.projects.edit-new
  (:require-macros
    [reagent.ratom :as ratom :refer [reaction]]
    [cljs.core.async.macros :refer [go]]

    )
  (:require
    [cider-ci.repository.constants :refer [CONTEXT]]
    [cider-ci.repository.request :as request]
    [cider-ci.repository.state :as state]
    [cider-ci.utils.core :refer [to-cistr presence]]
    [cider-ci.utils.url]

    [accountant.core :as accountant]
    [cljs.pprint :refer [pprint]]
    [reagent.core :as r ]
    ))


;### state ####################################################################

(def form-data (r/atom {}))

(def id (reaction (-> @state/client-state :current-page :id to-cistr presence)))

(def project (reaction (-> @state/server-state :repositories (get (keyword @id)))))

(def dissected-git-url
  (reaction
    (when-let[git-url (:git_url @form-data)]
      (cider-ci.utils.url/dissect git-url))))

(def git-url-valid?
  (reaction
    (-> @form-data :git_url clojure.string/blank? not)))

(def git-url-input-state
  (reaction
    (cond
      (not @git-url-valid?) "has-error"
      :else "")))

(def name-valid?
  (reaction
    (-> @form-data :name clojure.string/blank? not)))

(def name-input-state
  (reaction
    (cond
      (not @name-valid?) "has-error"
      :else "")))

(def form-valid?
  (reaction (and @git-url-valid? @name-valid?)))

(def request-response (r/atom {}))


;### helpers ##################################################################

(defn button-disabled? [disabled-fn?]
  (if (apply disabled-fn? [])
    {:disabled "YES"}
    {}))

(defn send-form-data []
  "If @id is set patches and otherwise posts the form data."
  (let [req {:method  (if @id :patch :post)
             :json-params @form-data
             :url (str CONTEXT "/projects/" @id)}]
    (request/send-off req [{:db request-response :keys []}])))


;##############################################################################
;### components ###############################################################
;##############################################################################

(defn toggled-section [title ks inputs & {:keys [description]} ]
  (fn [title ks inputs]
    [:section.permissions
     [:hr]
     [:h3
      [:a {:href nil ; TODO set "#" but this cause secretary to reroute
           :on-click #(swap! state/client-state assoc-in ks
                             (-> @state/client-state (get-in ks) not))}
       [:span
        (if (get-in @state/client-state ks false)
          [:i.fa.fa-caret-square-o-down]
          [:i.fa.fa-caret-square-o-right]) " "]
       title]]
     (when (not (clojure.string/blank? description))
       [:p description])
     (when (get-in @state/client-state ks false)
       [inputs])]))

;### Basic inputs #############################################################

(defn git-url-input []
  [:div.form-group {:class @git-url-input-state}
   [:label "Git url"]
   [:input#git_url.form-control
    {:placeholder "https://github.com/my/project.git"
     :on-change #(swap! form-data assoc :git_url (-> % .-target .-value))
     :value (-> @form-data :git_url)}]])

(defn name-input []
  [:div.form-group {:class @name-input-state}
   [:label "Project name"]
   [:input#name.form-control
    {:placeholder (or (-> @dissected-git-url :project_name) "My Project" )
     :on-change #(swap! form-data assoc :name (-> % .-target .-value))
     :on-focus #(when-let [suggested-project-name (:project_name @dissected-git-url)]
                  (when (clojure.string/blank? (:name @form-data))
                    (swap! form-data assoc :name  suggested-project-name)))
     :value (-> @form-data :name)}]
   [:p.help-block "A unique and mnemonic name."]])

(defn basic-inputs []
  [:div
   [:h3 "Basic settings"]
   [git-url-input]
   [name-input]])

;### permissions ##############################################################

(defn permissions-input-fields []
  [:div.checkbox
   [:label
    [:input
     {:type :checkbox
      ;:defaultChecked (-> @form-data :public_view_permission)
      :checked (-> @form-data :public_view_permission)
      :on-change #(swap! form-data (fn [fd] (assoc fd :public_view_permission (-> fd :public_view_permission not))))
      }]
    "Public view permission"]
   [:p.help-block
    "If this is checked the status of jobs, tasks, and trials belonging
    to this project can be " [:b " viewed " ]"  without being singed in. "
    "Visitors can also " [:b "download" ]" trials- and tree-attachments without being signed in. "
    "This needs to be "[:b "checked"]" if you use "[:b "badges"]"." ]])

(defn permissions-inputs []
  [toggled-section "Permissions"
   [:project-edit :toggle-show-permissions] permissions-input-fields
   :description "Set the public visibility."])

;### API ######################################################################

(def suggested-api-endpoint
  (reaction
    (when-let [hostname (:host @dissected-git-url)]
      (let [protocol (if (re-matches #"(?i)http.*" (:protocol  @dissected-git-url))
                       (:protocol  @dissected-git-url)
                       "https")]
        (str protocol "://" hostname)
        ))))

(defn api-endpoint-input []
  [:div.form-group
   [:label "Remote API endpoint"]
   [:input#remote_api_endpoint.form-control
    {:placeholder @suggested-api-endpoint
     :on-change #(swap! form-data assoc :remote_api_endpoint (-> % .-target .-value))
     :on-focus #(when @suggested-api-endpoint
                  (when (clojure.string/blank? (:remote_api_endpoint @form-data))
                    (swap! form-data assoc :remote_api_endpoint @suggested-api-endpoint)))
     :value (-> @form-data :remote_api_endpoint) }]
   [:p.help-block "The main entry point of the remove API, e.g. " [:code "https://github.com"] "."]])

(defn api-namespace-input []
  [:div.form-group
   [:label "Remote API project name-space"]
   [:input#remote_api_namespace.form-control
    {:placeholder (:project_namespace @dissected-git-url)
     :on-change #(swap! form-data assoc :remote_api_namespace (-> % .-target .-value))
     :on-focus #(when-let [suggested-api-namespace (:project_namespace @dissected-git-url)]
                  (when (clojure.string/blank? (:remote_api_namespace @form-data))
                    (swap! form-data assoc :remote_api_namespace suggested-api-namespace)))
     :value (-> @form-data :remote_api_namespace) }]
   [:p.help-block "The target namespace for API calls to the remove. "
    "Also known as the owner or organization."]])

(defn api-name-input []
  [:div.form-group
   [:label "Remote API project name"]
   [:input#remote_api_name.form-control
    {:placeholder (:project_name @dissected-git-url)
     :on-change #(swap! form-data assoc :remote_api_name (-> % .-target .-value))
     :on-focus #(when-let [suggested-api-name (:project_name @dissected-git-url)]
                  (when (clojure.string/blank? (:remote_api_name @form-data))
                    (swap! form-data assoc :remote_api_name suggested-api-name)))
     :value (-> @form-data :remote_api_name) }]
   [:p.help-block "The target name for API calls to the remote."]])

(defn api-token-input []
  [:div.form-group
   [:label "Remote API token"]
   [:input#api_token.form-control
    {:on-change #(swap! form-data assoc :remote_api_token (-> % .-target .-value))
     :value (-> @form-data :remote_api_token) }]
   [:p.help-block "The token used to authorize API calls to the remote."]])

(defn api-token-bearer-input []
  [:div.form-group
   [:label "Remote API token bearer"]
   [:input#api_token_bearer.form-control
    {:on-change #(swap! form-data assoc :remote_api_token_bearer (-> % .-target .-value))
     :value (-> @form-data :remote_api_token_bearer) }]
   [:p.help-block "The bearer of the token. Most remote services don't need this information."]])

(defn api-type-input []
  [:div.form-group
   [:label "Remote API type"]
   [:select#remote-api-type.form-control
    {:on-change #(swap! form-data assoc :remote_api_type (-> % .-target .-value))}
    [:option "github"]
    [:option "gitlab"]
    ]])

(defn api-input-fields []
  [:div
   [api-endpoint-input]
   [api-token-input]
   [api-token-bearer-input]
   [api-namespace-input]
   [api-name-input]
   [api-type-input]
   ])

(defn api-inputs []
  [toggled-section "API"
   [:project-edit :toggle-show-api] api-input-fields
   :description "You can set up parameters to push notifications and more."])

;### fetch ####################################################################

(defn fetch-interval-input []
  (let [default-interval "1 Minute"]
    (fn []
      [:div.form-group
       [:label "Fetch interval"]
       [:input#remote_fetch_interval.form-control
        {:placeholder default-interval
         :on-change #(swap! form-data assoc :remote_fetch_interval (-> % .-target .-value))
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
     :on-change #(swap! form-data assoc :update_notification_token (-> % .-target .-value))
     :value (-> @form-data :update_notification_token)}]
   [:p.help-block
    "This will be part of the URL to send update notifications from your git provider
    to this Cider-CI instance. "
    [:span.text-info "If you create a new project it is save and recommended to let this blank. A random token will be created in this case. "]
    [:span.text-warning "This value must be empty or a legal UUID."]]])

(defn fetch-inputs []
  [toggled-section "Git Fetch"
   [:project-edit :toggle-show-fetch]
   (fn [] [:div
           [fetch-interval-input]
           [notification-token-input]])
   :description "These settings control pulling from the remove."])


;### triggers #################################################################

(defn branch-trigger-include-match  []
  [:div.form-group
   [:label "Branch trigger include-match"]
   [:input#branch_trigger_include_match.form-control
    {:on-change #(swap! form-data assoc :branch_trigger_include_match (-> % .-target .-value))
     :value (-> @form-data :branch_trigger_include_match) }]])

(defn branch-trigger-exclude-match  []
  [:div.form-group
   [:label "Branch trigger exclude-match"]
   [:input#branch_trigger_exclude_match.form-control
    {:on-change #(swap! form-data assoc :branch_trigger_exclude_match (-> % .-target .-value))
     :value (-> @form-data :branch_trigger_exclude_match) }]])

(defn trigger-inputs []
  [toggled-section "Triggers"
   [:project-edit :toggle-show-triggers]
   (fn [] [:div
           [branch-trigger-include-match]
           [branch-trigger-exclude-match]])
   :description "You can override the triggers
                defined in the project configuration here."])


;### misc #####################################################################

(defn request-response-component []
  [:div.request-response
   (let [request-response @request-response
         status (-> request-response :response :status)
         pre-str (with-out-str (pprint request-response ))]
     (cond
       (= status 201) (let [id (-> request-response :response :body :id)
                            path (str CONTEXT "/projects/" id)]
                        (when (-> @state/server-state :repositories (get (keyword id)))
                          (accountant/navigate! path))
                        [:div.alert.alert-success
                         [:div [:strong "The project has been created."]]])

       (and (= "updated" (-> request-response :response :body :message))
            (= status 200)) (accountant/navigate! (str CONTEXT "/projects/" @id))

       status [:div.alert.alert-warning
               [:div [:strong "Oh snap! Something unexpected happened. "]
                "You might want to try again if the error appears to be transient."]
               [:pre pre-str]]

       (not (empty? request-response)) [:div.alert.alert-default
                                        [:pre pre-str]]
       :else nil))])

(defn cancel-submit-component []
  (when (not= 201 (:status @request-response))
    [:div.form-group.row
     [:div.col-xs-6 [:a.btn.btn-warning
                     {:href (str CONTEXT "/projects/" (or @id ""))}
                     [:i.fa.fa-arrow-circle-left] " Cancel "]]
     [:div.col-xs-6 [:button.pull-right.btn.btn-primary
                     (merge {:type :submit :on-click send-form-data}
                            (when (not @form-valid?) {:disabled "yes"}))
                     "Submit"]]]))

(defn debug-component []
  (when (:debug @state/client-state)
    [:div
     [:hr]
     [:h2 "Debug Local State"]
     [:pre
      (with-out-str
        (pprint
          {:form-data @form-data
           :dissected-git-url @dissected-git-url
           :git-url-valid? @git-url-valid?
           :git-url-input-state @git-url-input-state
           :request-response @request-response
           :id @id
           :project @project
           }))]]))

(defn form-component []
  (when (not= 201 (:status @request-response))
    [:div.form
     [basic-inputs]
     [permissions-inputs]
     [api-inputs]
     [fetch-inputs]
     [trigger-inputs]
     [:hr]
     ]))


;##############################################################################
;### new ######################################################################
;##############################################################################

(defn reset-for-new-state! []
  (doseq [k [:toggle-show-api
             :toggle-show-fetch
             :toggle-show-permissions
             :toggle-show-triggers]]
    (swap! state/client-state assoc-in [:project-edit k] false))
  (reset! form-data
          {:git_url  nil; "https://github.com/cider-ci/cider-ci_demo-project-bash.git"
           :name nil; "cider-ci_demo-project-bash"
           ;:remote_api_type "github"
           :remote_fetch_interval "1 Minute"
           :branch_trigger_include_match "^.*$"
           :branch_trigger_exclude_match ""
           })
  (reset! request-response nil))

(defn new-page []
  (r/create-class
    {:component-will-mount reset-for-new-state!
     :reagent-render
     (fn []
       [:div
        [:ol.breadcrumb
         [:li [:a {:href "/cider-ci/ui/public"} "Home"]]
         [:li [:a {:href (str CONTEXT "/projects/")} "Projects"]]
         [:li "Add a new project"]]
        [:div [:h1 "Add a new project"]]
        [form-component]
        [request-response-component]
        [cancel-submit-component]
        [debug-component]])}))


;##############################################################################
;### edit #####################################################################
;##############################################################################

(defn reset-for-edit-state! []
  (doseq [k [:toggle-show-api
             :toggle-show-fetch
             :toggle-show-permissions
             :toggle-show-triggers]]
    (swap! state/client-state assoc-in [:project-edit k] true))
  (reset! form-data
          (select-keys @project [:branch_trigger_exclude_match
                                 :branch_trigger_include_match
                                 :git_url
                                 :id
                                 :name
                                 :public_view_permission
                                 :remote_api_endpoint
                                 :remote_api_name
                                 :remote_api_namespace
                                 :remote_api_token
                                 :remote_api_token_bearer
                                 :remote_api_type
                                 :remote_fetch_interval
                                 :remote_http_fetch_token
                                 :update_notification_token
                                 ]))
  (reset! request-response {}))

(defn edit []
  (r/create-class
    {:component-will-mount reset-for-edit-state!
     :reagent-render
     (fn []
       [:div
        [:ol.breadcrumb
         [:li [:a {:href "/cider-ci/ui/public"} "Home"]]
         [:li [:a {:href (str CONTEXT "/projects/")} "Projects"]]
         [:li [:a {:href (str CONTEXT "/projects/" @id)} "Project"]]
         [:li "Edit project"]]
        [:div [:h1 "Edit project"]]
        [form-component]
        [request-response-component]
        [cancel-submit-component]
        [debug-component]])}))


