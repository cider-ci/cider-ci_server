(ns cider-ci.server.client.routes
  (:require
    [cider-ci.server.client.state :as state]

    [accountant.core :as accountant]
    [cljs-uuid-utils.core :as uuid]
    [secretary.core :as secretary :include-macros true]
    ))

(secretary/defroute create-admin-path "/cider-ci/create-initial-admin" []
  (swap! state/page-state assoc :current-page
         {:component "cider-ci.server.create-initial-admin.ui/page"}))

(secretary/defroute commits-path
  "/cider-ci/ui2/commits/" {:keys [query-params]}
  (swap! state/client-state assoc-in [:commits-page :form-data] query-params)
  (swap! state/page-state assoc :current-page
         {:component "cider-ci.server.ui2.commits.ui/page"
          :query-params query-params}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(secretary/defroute executor-path
  "/cider-ci/executors/:executor-id"
  {executor-id :executor-id}
  (swap! state/page-state
         assoc :current-page
         {:component "cider-ci.server.executors.ui.show/page"
          :executor-id executor-id}))

(secretary/defroute executor-edit-path
  "/cider-ci/executors/:executor-id/edit"
  {executor-id :executor-id}
  (swap! state/page-state
         assoc :current-page
         {:component "cider-ci.server.executors.ui.edit/page"
          :executor-id executor-id}))

(secretary/defroute executors-path
  "/cider-ci/executors/" {}
  (swap! state/page-state
         assoc :current-page
         {:component "cider-ci.server.executors.ui.index/page"}))

(secretary/defroute executors-create-path
  "/cider-ci/executors/:executor-id/create"
  {executor-id :executor-id}
  (swap! state/page-state
         assoc :current-page
         {:component "cider-ci.server.executors.ui.create/page"
          :executor-id executor-id }))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

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