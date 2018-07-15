; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.executor.attachments
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer :all])
  (:require
    [cider-ci.executor.utils.include-exclude :as in-ex]
    [cider-ci.executor.trials.helper :refer :all]
    [cider-ci.utils.core :refer :all]
    [cider-ci.utils.map :as map :refer [convert-to-array]]
    [cider-ci.utils.http :as http]
    [cider-ci.utils.config :as config :refer [get-config]]

    [logbug.debug :as debug]
    [logbug.catcher :as catcher]
    [clj-logging-config.log4j :as logging-config]
    [clj-time.core :as time]
    [clj-time.format :as time-format]
    [clojure.data.json :as json]
    [clojure.tools.logging :as logging]
    [me.raynes.fs :as fs]
    )
  (:import
    [java.io File]
    [java.nio.file Files FileSystems Path Paths]
    ))

(defn- put-file [file working-dir base-url content-type token]
  (catcher/with-logging {}
    (let [url (str base-url file)]
      (logging/debug "putting attachment"
                     {:file file :url url})
      (http/put url {:body (clojure.java.io/file
                             (str working-dir "/" file))
                     :content-type content-type
                     :headers {:trial-token token}
                     }))))

(defn nio-path [s]
  (.getPath (FileSystems/getDefault)
            s (make-array String 0)))

(defn put-attachments [dir-str in-ex-matcher trial]
  (let [dir-path (-> dir-str nio-path)]
    (->>(-> dir-str clojure.java.io/file file-seq)
            (map #(.toPath %))
            (filter #(Files/isRegularFile % (make-array java.nio.file.LinkOption 0)))
            (map #(.relativize dir-path %))
            (map #(.toString %))
            (filter #(in-ex/passes? % in-ex-matcher)))))

(defn- matching-paths-seq [dir-str in-ex-matcher]
  (let [dir-path (-> dir-str nio-path)]
    (->>(-> dir-str clojure.java.io/file file-seq)
            (map #(.toPath %))
            (filter #(Files/isRegularFile % (make-array java.nio.file.LinkOption 0)))
            (map #(.relativize dir-path %))
            (map #(.toString %))
            (filter #(in-ex/passes? % in-ex-matcher)))))

(defn- kind-property [kind postfix params]
  ((keyword (str kind postfix)) params))

; TODO  the base-url is for sure broken
; a GET URL looks like the following below ; from the routes def a PUT URL should be the same
;https://ci.zhdk.ch/cider-ci/storage/tree-attachments/4ef03438ea30e8c4ba63e9bff49ead9c6b1e862d/madek.tar.gz

(defn find-and-upload [trial]
  (let [params (-> trial get-params-atom deref)
        working-dir (get-working-dir trial)
        token (:token params)]
    (doseq [kind ["tree" "trial"]]
      (let [base-url (str (-> (get-config) :server-base-url :url)
                          "/storage/" kind "-attachments")]
        (when-let [matchers (kind-property kind "_attachments" params)]
          (doseq [matcher (convert-to-array matchers)]
            (let [content-type (:content_type matcher)]
              (when-not (:include_match matcher)
                (throw (ex-info (str "An attachment matcher must include "
                                     "the 'include_match' directive'.")
                                {:matcher matcher})))
              (doseq [path-str (matching-paths-seq working-dir matcher)]
                (catcher/snatch {}
                  (put-file path-str working-dir base-url content-type token)
                  )))))))))

;### Debug ####################################################################
;(debug/debug-ns *ns*)
;(debug/wrap-with-log-debug #'put-file)
;(logging-config/set-logger! :level :info)
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
