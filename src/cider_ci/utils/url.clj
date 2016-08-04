; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.utils.url
  (:require
    [cider-ci.utils.url.http :as url.http]
    [cider-ci.utils.url.ssh :as url.ssh]
    [cider-ci.utils.url.ssh-scp :as url.ssh-scp]

    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug :refer [I> I>> identity-with-logging]]
    [logbug.thrown :as thrown]
    ))




;### general ##################################################################

(defn dissect [url]
  (cond
    (re-matches url.http/pattern url) (url.http/dissect url)
    (re-matches url.ssh/pattern url) (url.ssh/dissect url)
    ; dissecting file urls file:// etc is not implemented (yet)
    ; the ssh-scp pattern is not very specific, it should come after the previous ones
    (re-matches url.ssh-scp/pattern url) (url.ssh-scp/dissect url)
    ))



;### Debug #####################################################################
;(debug/debug-ns *ns*)
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
