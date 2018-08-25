(ns cider-ci.server.resources.home.front
  (:refer-clojure :exclude [str keyword])
  (:require-macros
    [reagent.ratom :as ratom :refer [reaction]]
    )
  (:require
    [cider-ci.server.front.state :as state :refer [routing-state*]]
    [cider-ci.server.resources.auth.front :as auth]
    [cider-ci.server.front.breadcrumbs :as breadcrumbs]
    [cider-ci.server.paths :as paths]
    [cider-ci.server.executors.front.breadcrumbs :as executors-breadcrumbs]

    [cider-ci.utils.core :refer [keyword str presence]]
    ))

(defn page []
  [:div.root

   (when-let [user @state/user*]
     (breadcrumbs/nav-component
       [(breadcrumbs/home-li)]
       [(breadcrumbs/admin-li)
        (breadcrumbs/commits-li)
        (executors-breadcrumbs/executors-li)
        (breadcrumbs/projects-li)
        ]))

   [:div [:h1 "Cider-CI"]]

   ])
