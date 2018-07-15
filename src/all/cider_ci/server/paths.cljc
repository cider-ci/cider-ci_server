; Copyright © 2013 - 2018 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.  
(ns cider-ci.server.paths
  (:refer-clojure :exclude [str keyword])
  (:require
    [cider-ci.utils.core :refer [keyword str]]
    [bidi.verbose :refer [branch param leaf]]
    [bidi.bidi :as bidi :refer [path-for match-route]]
    [cider-ci.utils.url.query-params :refer [encode-query-params]]

    #?@(:clj
         [[clojure.tools.logging :as logging]
          [logbug.catcher :as catcher]
          [logbug.debug :as debug]
          [logbug.thrown :as thrown]
          ])))

(def user-api-tokens-paths
  (branch "/api-tokens"
          (leaf "/" :api-tokens)
          (leaf "new" :api-token-new)
          (branch ""
                  (param :api-token-id)
                  (leaf "" :api-token)
                  (leaf "/delete" :api-token-delete)
                  (leaf "/edit" :api-token-edit))))

(def user-email-addresses-paths
  (branch "/email-addresses"
          (leaf "/" :email-addresses)
          (leaf "/add" :email-addresses-add)
          (branch "/"
                  (param [#"[^/]*" :email-address])
                  (leaf "" :email-address))))

(def user-gpg-keys-paths
  (branch "/gpg-keys/"
          (leaf "" :gpg-keys)
          (leaf "add" :gpg-keys-add)
          (branch ""
                  (param :gpg-key-id)
                  (leaf "" :gpg-key)
                  (leaf "/edit" :gpg-key-edit))))


(def admin-users-paths
  (branch "/users"
          (leaf "/" :users)
          (leaf "/new" :user-new)
          (branch "/"
                  (param :user-id)
                  (leaf "" :user)
                  (leaf "/delete" :user-delete)
                  (leaf "/edit" :user-edit)
                  user-email-addresses-paths
                  user-api-tokens-paths
                  user-gpg-keys-paths)))

(def projects-paths 
  (branch "projects"
          (leaf "/" :projects)
          (leaf "/add" :projects-add)
          (branch "/"
                  (param [#":?[a-z][a-z0-9-_]+" :project-id])
                  (leaf "" :project)
                  (leaf "/delete" :project-delete)
                  (leaf "/edit" :project-edit)
                  (branch ".git"
                          (param [#".*" :repository-path]) 
                          (leaf "" :project-repository)))))

(def trees 
  (branch "trees/"
          (param :tree-id)
          (leaf "" :tree)
          (leaf "/attachments/" :tree-attachments)
          (branch "/attachmens/"
                  (param [#".*" :attachment-path]) 
                  (leaf "" :tree-attachment))
          (leaf "/project-configuration" :tree-project-configuration)))

(def jobs 
  (branch "jobs/"
          (leaf "" :jobs)))

(def authentication 
  (branch "auth"
          (leaf "" :auth)
          (leaf "/sign-in" :auth-sign-in)
          (leaf "/password-sign-in" :auth-password-sign-in)
          (leaf "/sign-out" :auth-sign-out)
          (leaf "/info" :auth-info)))

(def paths
  (branch "/"
          authentication
          (leaf "" :home)
          (leaf "commits/" :commits)
          trees
          jobs
          (branch "admin"
                  (leaf "/" :admin)
                  (leaf "/initial-admin" :initial-admin)
                  admin-users-paths)
          projects-paths
          (leaf "ws/chsk" :websockets)))

;(bidi/match-pair paths {:remainder "/projects/test.git/info/refs" :route paths})

(defn path
  "Caveat: query params will be properly encoded but the values of route params 
  must be passed already url-encoded. Note: encoding route params within breaks
  bidi."
  ([kw]
   (path-for paths kw))
  ([kw route-params]
   (apply (partial path-for paths kw)
          (->> route-params (into []) flatten)))
  ([kw route-params query-params]
   (str (path kw route-params) "?"
        (encode-query-params query-params))))


;(path :websockets)
