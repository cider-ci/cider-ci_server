(ns cider-ci.executor.self-update
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer :all])
  (:require
    [cider-ci.utils.config :refer [get-config]]
    [clojure.java.io :as io]

    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug]
    )
  (:import
    [java.util.concurrent.locks ReentrantLock]
    [java.io File]
    [java.nio.file Files CopyOption StandardCopyOption]
    ))

(def self-update-lock (ReentrantLock.))


(defn nio-path [s]
  (java.nio.file.Paths/get s (into-array [""])))

(defn move!
  {:tag java.nio.file.Path}
  [source target]
  (Files/move (nio-path source) (nio-path target)
              (into-array CopyOption [StandardCopyOption/ATOMIC_MOVE
                                      StandardCopyOption/REPLACE_EXISTING])))

(defn download []
  (let [tmpdir (System/getProperty "user.dir")
        tmppath (clojure.string/join File/separator [tmpdir "executor.jar.download"])
        target  (clojure.string/join File/separator
                                     [(System/getProperty "user.dir")
                                      "executor.jar"])
        url (str (:server_base_url (get-config))
                 "/cider-ci/downloads/executor/executor.jar")]
    (with-open [in (io/input-stream url)
                out (io/output-stream tmppath)]
      (io/copy in out))
    (move! tmppath target)))

(defn exit []
  (System/exit 0))

(defn self-update! []
  (when-not (.isLocked self-update-lock)
    (.lock self-update-lock)
    (try
      (catcher/with-logging {}
        (logging/info "Performing self-update")
        (download)
        (exit))
      (finally
        (.unlock self-update-lock)))))


;### Debug ####################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns 'cider-ci.auth.http-basic)
;(debug/debug-ns *ns*)
