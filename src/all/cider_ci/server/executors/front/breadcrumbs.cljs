; Copyright © 2013 - 2018 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.server.executors.front.breadcrumbs
  (:refer-clojure :exclude [str keyword])
  (:require-macros
    [reagent.ratom :as ratom :refer [reaction]]
    [cljs.core.async.macros :refer [go]])
  (:require
    [cider-ci.server.front.breadcrumbs :as breadcrumbs]
    [cider-ci.server.front.breadcrumbs :as breadcrumbs]
    [cider-ci.server.front.requests.core :as requests]
    [cider-ci.server.front.icons :as icons]
    [cider-ci.server.front.shared :as shared :refer [humanize-datetime-component]]
    [cider-ci.server.front.state :as state :refer [routing-state*]]
    [cider-ci.server.paths :as paths :refer [path]]
    [cider-ci.server.resources.api-token.front :as api-token]
    [cider-ci.server.resources.auth.front :as auth]

    [cider-ci.utils.core :refer [keyword str presence]]
    [cider-ci.utils.seq :refer [with-index]]

    [accountant.core :as accountant]
    [cljs.pprint :refer [pprint]]
    [cljs.core.async :as async]
    [cljs.core.async :refer [timeout]]
    [reagent.core :as reagent]

    ))


(def icon-executor [:i.fas.fa-cog])
(def icon-executors [:i.fas.fa-cog])

(defn executors-li [] 
  (breadcrumbs/li :executors [:span icon-executors " Executors "]))

(defn executor-li [id] 
  (breadcrumbs/li :executor [:span icon-executors " Executor "] {:executor-id id}))

(defn executors-add-li [] 
  (breadcrumbs/li :executors-add [:span icons/add " Add "]))
