; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.utils.url.ssh
  (:require
    [cider-ci.utils.url.shared :refer :all]

    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug :refer [I> I>> identity-with-logging]]
    [logbug.thrown :as thrown]
    ))

; ssh://user@server/project-namespace/project-name.git
; user@server:project-namespace/project-name.git
; git@github.com:cider-ci/cider-ci.git
; server:project-namespace/project-name.git
; ssh://[user@]host.xz[:port]/path/to/repo.git/
; [user@]host.xz:path/to/repo.git/


(def pattern
  #"(?i)(ssh)://([^@]+@)?([^/]+)(/.*)")

(defn ssh-url-basic-dissect [url]
  (as-> url url
    (re-matches pattern url)
    {:protocol (-> url (nth 1) clojure.string/lower-case)
     :authentication_with_at (nth url 2)
     :host_port (nth url 3)
     :path (nth url 4)
     :parts url
     :url (nth url 0)}))

(defn dissect [url]
  (as-> url url
    (ssh-url-basic-dissect url)
    (merge url (host-port-dissect (:host_port url)))
    (merge url (auth-dissect (:authentication_with_at url)))
    (merge url (path-dissect (:path url)))))


;### Debug #####################################################################
;(debug/debug-ns *ns*)
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
