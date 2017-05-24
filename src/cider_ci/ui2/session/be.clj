; Copyright © 2016 - 2017 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.ui2.session.be
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer [keyword str]])

  (:require
    [cider-ci.ui2.constants :refer [CONTEXT]]
    [cider-ci.ui2.session.oauth :as oauth]
    [cider-ci.ui2.session.password.be :as password]
    [cider-ci.ui2.session.shared :refer [sign-out]]

    [compojure.core :as cpj]

    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.debug :as debug :refer [I> I>> identity-with-logging]]
    [logbug.ring :refer [wrap-handler-with-logging]]
    [logbug.thrown :as thrown]
    ))

(def routes
  (cpj/routes
    (cpj/POST "/session/sign-out" _ #'sign-out)
    (cpj/POST "/session/password/sign-in" _ #'password/sign-in)
    (cpj/POST "/session/oauth/request-sign-in" [] #'oauth/request-sign-in)
    (cpj/GET "/session/oauth/:type/sign-in" [] #'oauth/sign-in)
    ))


;#### debug ###################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/wrap-with-log-debug #'password-sign-in)
;(debug/debug-ns 'cider-ci.open-session.bcrypt)
;(debug/debug-ns 'cider-ci.open-session.signature)
;(debug/debug-ns 'cider-ci.open-session.encryptor)
;(debug/debug-ns 'cider-ci.open-session.encryptor)
;(debug/debug-ns *ns*)
