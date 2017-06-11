; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.
;

(ns cider-ci.executor.utils
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer :all])
  (:require
    [cider-ci.utils.config :refer [get-config]]
    [cider-ci.constants]

    [clojure.java.io :as io]

    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug]
    )
  (:import
    [java.io Closeable File InputStream OutputStream PipedInputStream PipedOutputStream]
    [java.nio.charset Charset]
    )
  )


(def charset (Charset/forName "UTF-8"))


(defn build-continuous-output-reader
  [output-handler finished-fn
   & {:keys [buffer-size sleep-ms]
      :or {buffer-size (* 100 1024) sleep-ms 1000}}]
  (catcher/with-logging {}
    (let [buffer (make-array Byte/TYPE buffer-size)
          is (PipedInputStream. buffer-size)
          os (PipedOutputStream. is)]
      (future
        (catcher/snatch {}
          (with-open [is is]
            (loop []
              (.flush os)
              (let [len (if (finished-fn)
                          buffer-size
                          (.available is))
                    bytes-read (.read is buffer 0 len)]
                (when (not= bytes-read -1)
                  (when (< 0 bytes-read)
                    (let [output (String. buffer 0 bytes-read charset)]
                      (output-handler output)))
                  (Thread/sleep sleep-ms)
                  (recur)))))))
      os)))


(def terminal-states
  (-> cider-ci.constants/STATES :FINISHED set))


;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns *ns*)
