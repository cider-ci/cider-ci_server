(ns cider-ci.client.routes
  (:require
    [cider-ci.client.state :as state]

    [accountant.core :as accountant]
    [cljs-uuid-utils.core :as uuid]
    [secretary.core :as secretary :include-macros true]
    ))

(secretary/defroute commits-path
  "/cider-ci/ui2/commits/" {:keys [query-params]}
  (swap! state/client-state assoc-in [:commits-page :form-data] query-params)
  (swap! state/page-state assoc :current-page
         {:component "cider-ci.ui2.commits.ui/page"
          :query-params query-params}))

(secretary/defroute user-api-token-path
  "/cider-ci/users/:user-id/api-tokens/:api-token-id"
  {user-id :user-id api-token-id :api-token-id}
  (swap! state/page-state
         assoc :current-page
         {:component "cider-ci.users.api-tokens.ui.show/page"
          :user-id user-id
          :api-token-id api-token-id}))

(secretary/defroute user-api-token-edit-path
  "/cider-ci/users/:user-id/api-tokens/:api-token-id/edit"
  {user-id :user-id api-token-id :api-token-id}
  (swap! state/page-state
         assoc :current-page
         {:component "cider-ci.users.api-tokens.ui.edit/page"
          :user-id user-id
          :api-token-id api-token-id}))

(secretary/defroute user-api-tokens-path
  "/cider-ci/users/:user-id/api-tokens/" {user-id :user-id}
  (swap! state/page-state
         assoc :current-page
         {:component "cider-ci.users.api-tokens.ui.index/page"
          :user-id user-id}))

(secretary/defroute user-api-tokens-create-path
  "/cider-ci/users/:user-id/api-tokens/:api-token-id/create"
  {user-id :user-id
   api-token-id :api-token-id}
  (swap! state/page-state
         assoc :current-page
         {:component "cider-ci.users.api-tokens.ui.create/page"
          :user-id user-id
          :api-token-id api-token-id
          }))
