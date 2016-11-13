; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.repository.remote
  (:require
    [cider-ci.utils.url :as url]
    [cider-ci.utils.core :refer [presence]]
    ))

(defn api-endpoint [repository]
  (presence (or (:remote_api_endpoint repository)
                (when (= "github.com"
                         (-> repository :git_url url/dissect :host))
                  "https://api.github.com"))))

(defn api-endpoint! [repository]
  (or (api-endpoint repository)
      (throw (ex-info "The required api-endpoint could not be inferred"
                      {:repository repository}))))

(defn api-namespace [repository]
  (presence (or (:remote_api_namespace repository)
                (-> repository :git_url url/dissect :project_namespace))))

(defn api-namespace! [repository]
  (or (api-namespace repository)
      (throw (ex-info "The required api-namespace could not be inferred."
                      {:repository repository}))))

(defn api-name [repository]
  (presence (or (:remote_api_name repository)
                (-> repository :git_url url/dissect :project_name))))

(defn api-name! [repository]
  (or (api-name repository)
      (throw (ex-info "The required api-name could not be inferred."
                      {:repository repository}))))

(defn github-api-access? [project]
  (boolean (and
             (or (= "github" (:remote_api_type project))
                 (= "github.com" (-> project :git_url
                                     cider-ci.utils.url/dissect :host)))
             (presence (:remote_api_token project))
             (api-endpoint project)
             (api-namespace project)
             (api-name project))))

(defn api-type [project]
  (or (:remote_api_type project)
      (cond
        (= "github.com" (-> project :git_url cider-ci.utils.url/dissect :host)) "github"
        :else nil)))

(defn api-access? [project]
  (case (api-type project)
    "github" (github-api-access? project)
    false))

