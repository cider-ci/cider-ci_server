; Copyright © 2013 - 2018 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.server.executors.front.index
  (:refer-clojure :exclude [str keyword])
  (:require-macros
    [reagent.ratom :as ratom :refer [reaction]]
    [cljs.core.async.macros :refer [go]])
  (:require
    [cider-ci.server.resources.api-token.front :as api-token]
    [cider-ci.server.front.requests.core :as requests]
    [cider-ci.server.front.breadcrumbs :as breadcrumbs]
    [cider-ci.server.executors.front.breadcrumbs :as executor-breadcrumbs]
    [cider-ci.server.front.state :as state :refer [routing-state*]]
    [cider-ci.server.paths :as paths :refer [path]]
    [cider-ci.server.resources.auth.front :as auth]
    [cider-ci.server.front.shared :as shared :refer [humanize-datetime-component]]

    [cider-ci.utils.core :refer [keyword str presence]]
    [cider-ci.utils.seq :refer [with-index]]

    [accountant.core :as accountant]
    [cljs.pprint :refer [pprint]]
    [cljs.core.async :as async]
    [cljs.core.async :refer [timeout]]
    [reagent.core :as reagent]

    ))


(defn page []
  [:div.executors-page
   (breadcrumbs/nav-component
     [(breadcrumbs/home-li)
      (executor-breadcrumbs/executors-li)]
     [(executor-breadcrumbs/executors-add-li)])
   [:h1 " Executors "]
   ])

