(ns cider-ci.server.client.routes
  (:require
    [cider-ci.server.client.state :as state]
    [cider-ci.utils.core :refer [presence keyword str]]

    [cljs.pprint :refer [pprint]]
    [accountant.core :as accountant]
    [cljs-uuid-utils.core :as uuid]
    [secretary.core :as secretary :include-macros true]
    ))

;;; initial admin ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(secretary/defroute create-admin-path "/cider-ci/create-initial-admin" []
  (swap! state/page-state assoc :current-page
         {:component "cider-ci.server.create-initial-admin.ui/page"}))


;;; commits ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(secretary/defroute commits-path
  "/cider-ci/commits/" {:keys [query-params]}
  (let [parsed-query-params  (->> query-params
                                  (map (fn [[k v]]
                                         (try
                                           [k (-> v js/JSON.parse js->clj)]
                                           (catch js/Object _
                                             (js/console.error
                                               (str "Can not parse \"" v "\" as JSON, dropping \"" k ))
                                             nil))))
                                  (filter identity)
                                  (into {}))
        heads-only (if (contains? parsed-query-params :heads-only)
                     (:heads-only parsed-query-params)
                     true)
        per-page (or (-> parsed-query-params :per-page presence) 12)
        page (or (-> parsed-query-params :page presence) 1)
        normalized-query-params (-> parsed-query-params
                                    (assoc :heads-only heads-only)
                                    (assoc :per-page per-page)
                                    (assoc :page page))]
    ;(js/console.log (with-out-str (pprint ["commits-path" {:query-params query-params :parsed-query-params parsed-query-params}])))
    (swap! state/client-state assoc-in [:commits-page :form-data] normalized-query-params)
    (swap! state/page-state assoc :current-page
           {:component "cider-ci.server.commits.ui/page"
            :query-params normalized-query-params})))


;;; settings ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(secretary/defroute settings-path
  "/cider-ci/settings/" {}
  (swap! state/page-state assoc :current-page
         {:component "cider-ci.server.settings.ui/page" }))

(secretary/defroute settings-section-path
  "/cider-ci/settings/:section" {section :section}
  (swap! state/page-state assoc :current-page
         {:component (str "cider-ci.server.settings.ui/page")
          :section (keyword section)}))


;;; connections ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(secretary/defroute connection-path
  "/cider-ci/connection/" {}
  (swap! state/page-state assoc :current-page
         {:component "cider-ci.server.client.connection/page"}))

(secretary/defroute requests-path
  "/cider-ci/connection/requests/" {}
  (swap! state/page-state assoc :current-page
         {:component "cider-ci.server.client.connection.request/page"}))

(secretary/defroute socket-path
  "/cider-ci/connection/socket/" {}
  (swap! state/page-state assoc :current-page
         {:component "cider-ci.server.client.connection.socket/page"}))

;;; executor ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(secretary/defroute executor-path
  "/cider-ci/executors/:executor-id"
  {executor-id :executor-id}
  (swap! state/page-state
         assoc :current-page
         {:component "cider-ci.server.executors-old.ui.show/page"
          :executor-id executor-id}))

(secretary/defroute executor-edit-path
  "/cider-ci/executors/:executor-id/edit"
  {executor-id :executor-id}
  (swap! state/page-state
         assoc :current-page
         {:component "cider-ci.server.executors-old.ui.edit/page"
          :executor-id executor-id}))

(secretary/defroute executors-path
  "/cider-ci/executors/" {}
  (swap! state/page-state
         assoc :current-page
         {:component "cider-ci.server.executors-old.ui.index/page"}))

(secretary/defroute executors-create-path
  "/cider-ci/executors/:executor-id/create"
  {executor-id :executor-id}
  (swap! state/page-state
         assoc :current-page
         {:component "cider-ci.server.executors-old.ui.create/page"
          :executor-id executor-id }))


;;; treeish ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(secretary/defroute tree-path
  "/cider-ci/trees/:tree-id"
  {tree-id :tree-id}
  (swap! state/page-state
         assoc :current-page
         {:component "cider-ci.server.trees.ui/page"
          :tree-id tree-id}))

(secretary/defroute project-configuration-path
  "/cider-ci/trees/:tree-id/project-configuration"
  {tree-id :tree-id}
  (swap! state/page-state
         assoc :current-page
         {:component "cider-ci.server.trees.ui.project-configuration/page"
          :tree-id tree-id}))

(secretary/defroute runnalbe-jobs-path
  "/cider-ci/trees/:tree-id/available-jobs/"
  {tree-id :tree-id}
  (swap! state/page-state
         assoc :current-page
         {:component "cider-ci.server.trees.ui.available-jobs/page"
          :tree-id tree-id}))

(secretary/defroute tree-attachments-path
  "/cider-ci/trees/:tree-id/tree-attachments/"
  {tree-id :tree-id}
  (swap! state/page-state
         assoc :current-page
         {:component "cider-ci.server.trees.attachments.ui/page"
          :tree-id tree-id}))

(secretary/defroute tree-attachment-path
  "/cider-ci/trees/:tree-id/tree-attachments/:path"
  {tree-id :tree-id
   path :path}
  (swap! state/page-state
         assoc :current-page
         {:component "cider-ci.server.trees.attachments.show/page"
          :tree-id tree-id
          :path (js/decodeURIComponent path)
          }))


;;; tokens ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(secretary/defroute user-api-token-path
  "/cider-ci/users/:user-id/api-tokens/:api-token-id"
  {user-id :user-id api-token-id :api-token-id}
  (swap! state/page-state
         assoc :current-page
         {:component "cider-ci.server.users.api-tokens.ui.show/page"
          :user-id user-id
          :api-token-id api-token-id}))

(secretary/defroute user-api-token-edit-path
  "/cider-ci/users/:user-id/api-tokens/:api-token-id/edit"
  {user-id :user-id api-token-id :api-token-id}
  (swap! state/page-state
         assoc :current-page
         {:component "cider-ci.server.users.api-tokens.ui.edit/page"
          :user-id user-id
          :api-token-id api-token-id}))

(secretary/defroute user-api-tokens-path
  "/cider-ci/users/:user-id/api-tokens/" {user-id :user-id}
  (swap! state/page-state
         assoc :current-page
         {:component "cider-ci.server.users.api-tokens.ui.index/page"
          :user-id user-id}))

(secretary/defroute user-api-tokens-create-path
  "/cider-ci/users/:user-id/api-tokens/:api-token-id/create"
  {user-id :user-id
   api-token-id :api-token-id}
  (swap! state/page-state
         assoc :current-page
         {:component "cider-ci.server.users.api-tokens.ui.create/page"
          :user-id user-id
          :api-token-id api-token-id
          }))
