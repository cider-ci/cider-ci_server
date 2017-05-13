(ns cider-ci.users.web
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer [keyword str]])
  (:require
    [cider-ci.users.api-tokens.core :as api-tokens]

    [compojure.core :as cpj]
    [ring.util.response]

    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.debug :as debug :refer [I> I>> identity-with-logging]]
    [logbug.ring :refer [wrap-handler-with-logging]]
    [logbug.thrown :as thrown]
    ))


(defn dead-end-handler [req]
  {:status 404
   :body "Not found in /users !"})


(def routes
  (cpj/routes
    (cpj/ANY "/users/:id/api-tokens/*" [] api-tokens/routes)
    (cpj/ANY "*" [] dead-end-handler)
    ))



