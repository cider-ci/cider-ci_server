; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.utils.url.ssh-scp
  (:require
    [cider-ci.utils.url.shared :refer [host-port-dissect path-dissect auth-dissect]]
    ))

(def pattern
  #"([^@]+@)?([^:]+):(.*)")

(defn basic-dissect [url]
  (as-> url url
    (re-matches pattern url)
    {:protocol "ssh"
     :authentication_with_at (nth url 1)
     :host_port (nth url 2)
     :path (nth url 3)
     :parts url
     :url (nth url 0)}))

(defn dissect [url]
  (as-> url url
    (basic-dissect url)
    (merge url (host-port-dissect (:host_port url)))
    (merge url (auth-dissect (:authentication_with_at url)))
    (merge url (path-dissect (:path url)))))
