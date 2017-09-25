; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.executor.accepted-repositories
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer :all])
  (:require
    [cider-ci.executor.utils.tags :refer :all]
    [cider-ci.utils.fs :refer :all]
    [cider-ci.utils.config :as config :refer [get-config merge-into-conf]]

    [clojure.java.io :as io]
    [clojure.set :refer [union]]
    [clojure.string :refer [blank? lower-case split trim]]
    [clojure.tools.logging :as logging]
    [logbug.debug :as debug]
 ))

(defn assert-satisfied [repository-url]
  (let [accepted-repositories (:accepted_repositories (get-config))]
    (when (clojure.string/blank? repository-url)
      (throw (ex-info "The repository-url may not be empty."
                      {:status 422
                       :repository-url repository-url
                       :accepted-repositories accepted-repositories})))
    (or (some #{repository-url} accepted-repositories)
        (some (fn [accepted-repository]
                (when (re-find #"^\^.*\$$" accepted-repository)
                  (re-matches (re-pattern accepted-repository) repository-url)
                  )) accepted-repositories)
        (throw (ex-info "The repository-url is not included in the accepted repositories."
                        {:status 403
                         :repository-url repository-url
                         :accepted-repositories accepted-repositories})))))

;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
