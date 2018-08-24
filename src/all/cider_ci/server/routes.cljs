(ns cider-ci.server.routes
  (:refer-clojure :exclude [str keyword])
  (:require-macros
    [reagent.ratom :as ratom :refer [reaction]]
    )
  (:require

    [cider-ci.server.front.pages.debug]
    [cider-ci.server.front.pages.root]
    [cider-ci.server.front.requests.pages.request]
    [cider-ci.server.front.requests.pages.requests]
    [cider-ci.server.front.state :refer [routing-state*]]
    [cider-ci.server.paths :refer [path paths]]

    [cider-ci.server.resources.admin.front :as admin]
    [cider-ci.server.resources.api-token.front :as api-token]
    [cider-ci.server.resources.api-tokens.front :as api-tokens]
    [cider-ci.server.resources.auth.front :as auth]
    [cider-ci.server.resources.commits.front :as commits]
    [cider-ci.server.resources.email-addresses.front :as email-addresses]
    [cider-ci.server.resources.gpg-keys.front :as gpg-keys]
    [cider-ci.server.resources.home.front :as home]
    [cider-ci.server.resources.initial-admin.front :as initial-admin]
    [cider-ci.server.resources.projects.front :as projects]
    [cider-ci.server.resources.status.front :as status]
    [cider-ci.server.resources.user.front :as user]
    [cider-ci.server.resources.users.front :as users]
    [cider-ci.server.trees.front :as trees]

    [cider-ci.utils.core :refer [keyword str presence]]
    [cider-ci.utils.url.query-params :refer [decode-query-params]]

    [accountant.core :as accountant]
    [bidi.bidi :as bidi]
    [clojure.pprint :refer [pprint]]
    [reagent.core :as reagent]
    ))

(def page-resolve-table
  {:admin admin/page
   :api-token api-token/show-page
   :api-token-delete api-token/delete-page
   :api-token-edit api-token/edit-page
   :api-token-new api-token/new-page
   :api-tokens api-tokens/page
   :auth-password-sign-in auth/password-sign-in-page
   :auth-sign-in auth/sign-in-page
   :auth-info auth/info-page
   :commits #'commits/page
   :email-addresses email-addresses/index-page
   :email-addresses-add email-addresses/add-page
   :gpg-key gpg-keys/show-page
   :gpg-key-edit gpg-keys/edit-page
   :gpg-keys gpg-keys/index-page
   :gpg-keys-add gpg-keys/add-page
   :home home/page
   :initial-admin initial-admin/page
   :project projects/show-page
   :project-delete projects/delete-page
   :project-edit projects/edit-page
   :projects projects/index-page
   :projects-add projects/add-page
   :status status/info-page
   :tree trees/page
   :user user/show-page
   :user-delete user/delete-page
   :user-edit user/edit-page
   :user-new user/new-page
   :users users/page
   })

(def event-handler-resolve-table 
  {:commits {:table-names #{"projects" "branches"}
             :handler #'commits/event-handler 
             }})

(defn resolve-page [k]
  (get page-resolve-table k nil))

(defn resolve-event-handler [k]
  (get event-handler-resolve-table k nil))


(defn match-path [path]
  (bidi/match-route paths path))

(defn init-navigation []
  (accountant/configure-navigation!
    {:nav-handler (fn [path]
                    (let [{route-params :route-params
                           handler-key :handler} (match-path path)
                          location-href (-> js/window .-location .-href)
                          location-url (goog.Uri. location-href)]
                      (swap! routing-state* assoc
                             :route-params route-params
                             :handler-key handler-key
                             :page (resolve-page handler-key)
                             :event-handler (resolve-event-handler handler-key)
                             :url location-href
                             :path (.getPath location-url)
                             :query-params (-> location-url .getQuery decode-query-params))
                      ;(js/console.log (with-out-str (pprint [handler-key route-params])))
                      ))
     :path-exists? (fn [path]
                     ;(js/console.log (with-out-str (pprint (match-path path))))
                     (boolean (match-path path)))}))

(defn init []
  (init-navigation)
  (accountant/dispatch-current!))
