; Copyright © 2017 - 2017 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.utils.http-resources-cache-buster
  (:require

    [cider-ci.utils.sha1 :as sha1]

    [ring.middleware.resource :as resource]
    [ring.util.codec :as codec]
    [ring.util.request :as request]
    [ring.util.response :as response]

    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.debug :as debug :refer [I> I>> identity-with-logging]]
    [logbug.ring :refer [wrap-handler-with-logging]]
    [logbug.thrown :as thrown]
    ))

(def cache-buster-path->original-path (atom {}))
(def original-path->cache-buster-path (atom {}))

(defn cache-busted-path [path]
  "Returns either the cache-buster-path if this path is cached
  or the path itself"
  (get @original-path->cache-buster-path path path))

(defn to-be-cached? [path xp]
  (boolean
    (some (fn [p]
            (if (string? p)
              (= p path)
              (re-find p path)))
          xp)))

(defn extension [path]
  (->> path
       (re-matches #".*\.([^\.]+)")
       last))

(defn cache-bust-path! [path readable]
  (let [signature (-> readable slurp sha1/sha1)
        extension (extension path)
        cache-buster-path (str path "_" signature "." extension)]
    (swap! cache-buster-path->original-path assoc cache-buster-path path)
    (swap! original-path->cache-buster-path assoc path cache-buster-path)
    cache-buster-path))

(defn resource [handler root-path options request]
  (let [path (codec/url-decode (request/path-info request))]
    (logging/debug {:path path})
    (if-let [original-path (get @cache-buster-path->original-path path nil)]
      (do
        (logging/debug {:original-path original-path})
        (assoc-in
          (resource/resource-request
            (assoc request :path-info (codec/url-encode original-path))
            root-path (dissoc options :cached-paths))
          [:headers "Cache-Control"] "public, max-age=31536000"))
      (if-let [uncached-response (resource/resource-request
                                   request root-path (dissoc options :cached-paths))]
        (if (and (:body uncached-response)
                 (to-be-cached? path (:cached-paths options)))
          (let [new-path (cache-bust-path! path (:body uncached-response))]
            (ring.util.response/redirect (str (:context request) new-path)))
          uncached-response)
        (handler request)))))

(defn wrap-resource
  "Replacement for ring.middleware.resource/wrap-resource.  Takes an
  additional option :cached-paths where the value is a collection. Each value
  is either a string or a regex.  Caching is lazy and will be effective only
  with the second get request to the resource."
  ([handler root-path]
   (wrap-resource handler root-path {:cached-paths []}))
  ([handler root-path options]
   (fn [request]
     (resource handler root-path options request))))


;#### debug ###################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns 'cider-ci.utils.shutdown)
;(debug/debug-ns *ns*)
