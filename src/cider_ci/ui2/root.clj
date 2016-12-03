; Copyright © 2016 - 2017 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.ui2.root
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer [keyword str presence]])

  (:require
    [cider-ci.ui2.constants :refer [CONTEXT]]

    [cider-ci.utils.rdbms :as rdbms]
    [cider-ci.utils.markdown :refer [md2html]]
    [cider-ci.utils.self]

    [clojure.java.jdbc :as jdbc]

    [logbug.debug :as debug]
    ))


(def default-welcome-message
"
# Welcome to Cider-CI

This is the default welcome-message.

It can be customized via `Administration` → `Welcome page settings`!
")


(def about-pre-message
"
# About Cider-CI
Cider-CI is an application and service stack
for highly **parallelized and resilient testing**, and
**continuous delivery**.

Read more about Cider-CI at [cider-ci.info](http://cider-ci.info/).
")

(defn about-release-message []
  (md2html (or (-> (cider-ci.utils.self/release) :about-name presence)
               "**No about-release message defined!**")))

(defn welcome-message []
  (-> (or (->> ["SELECT welcome_message FROM welcome_page_settings"]
               (jdbc/query (rdbms/get-ds))
               first :welcome_message presence)
          default-welcome-message)
      md2html))

(defn page [req]
  [:div#welcome-page
   [:div#welcome-message.text-center
    (welcome-message)]
   [:hr]
   [:div#about-message.text-center
    (md2html about-pre-message)
    (about-release-message)]])

;(debug/debug-ns *ns*)
