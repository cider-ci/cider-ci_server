; Copyright (C) 2013, 2014, 2015 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.


(ns cider-ci.utils.messaging
  (:require
    [clj-logging-config.log4j :as  logging-config]
    [clojure.data.json :as json]
    [clojure.tools.logging :as logging]
    [langohr.basic :as lb]
    [langohr.channel :as lch]
    [langohr.consumers :as lcons]
    [langohr.core :as rmq]
    [langohr.exchange :as le]
    [langohr.queue :as lq]
    [logbug.debug :as debug]
    [logbug.catcher :as catcher]
    [cider-ci.utils.json-protocol]
    )
  (:import
    [java.util.concurrent Executors]
    ))


(def conf (atom {}))

;### connection handling ######################################################
(defonce ^:private conn (atom nil))
(defonce ^:private ch (atom nil))
(defn- connect
  ([]
   (connect {}))
  ([conn-conf]
   (logging/debug [connect conn-conf])
   (reset! conn
           (rmq/connect
             (conj {:executor (Executors/newFixedThreadPool 4)
                    :automatically-recover true}
                   (:connection @conf))))))

(defn disconnect []
  (catcher/snatch {} (rmq/close @ch))
  (catcher/snatch {} (rmq/close @conn))
  (reset! ch nil)
  (reset! conn nil)
  )

(defmacro with-channel [ch-sym & body]
  `(let [~ch-sym (lch/open @conn)]
     (try
       ~@body
       (finally
         (when (lch/open? ~ch-sym)
           (lch/close ~ch-sym))))))

  ;(macroexpand-1 '(with-channel x (println x)))
(defn- get-channel []
  (when-not @ch
    (reset! ch (lch/open @conn)))
  (when-not (lch/open?  @ch)
    (reset! ch (lch/open @conn)))
  @ch)


;### logging ##################################################################
(defonce ^:private logging-queue (atom nil))
(defn- logging-receiver [ch metadata ^bytes payload]
  (let [message (try
                  (clojure.walk/keywordize-keys
                    (json/read-str (String. payload "UTF-8")))
                  (catch Exception _
                    "message decode failed")) ]
    (logging/debug ["MESSAGE LOGGING" {:metadata metadata
                              :payload payload
                              :message message}])))
(defn- bind-to-logging-queue [exchange-name]
  (lq/bind (get-channel) (:queue @logging-queue) exchange-name {:routing-key "#"}))


;### utils (low level) ########################################################
(defn- exchange? [name]
  (with-channel ch
    (try
      (le/declare-passive ch name)
      true
      (catch java.io.IOException e
        false))))

(defn- create-handler [message-receiver]
  (fn [ch metadata ^bytes payload]
    (catcher/snatch {}
      (logging/debug {:message (conj
                                 (select-keys metadata [:type :exchange])
                                 {:payload (String. payload "UTF-8")})})
      (let [message (clojure.walk/keywordize-keys
                      (json/read-str (String. payload "UTF-8")))]
        (message-receiver message)))))

(defn- create-exchange
  ([exchange-name]
   (create-exchange exchange-name "topic" {}))
  ([exchange-name exchange-type options]
   (with-channel _ch
     (le/declare _ch exchange-name exchange-type
                 (conj {:durable true
                        :auto-delete false
                        :internal false}
                       options)))
   (bind-to-logging-queue exchange-name)))

(defn- create-queue [queue-name options]
  (lq/declare @ch queue-name
              (conj
                {:durable false :exclusive true :auto-delete true}
                options)))


;### publish helper ##########################################################
(defonce ^:private memoized-created-topics (atom #{}))
(defn- memoized-create-exchange [name]
  (let [hkey (str name "_" (.hashCode @conn) )]
    (when-not (get hkey @memoized-created-topics)
      (create-exchange name)
      (swap! memoized-created-topics
             (fn [curr hkey] (conj curr hkey))
             hkey))))


;### high level api / dsl #####################################################
(defn publish
  "Publish a (ad hoc) message in json format. Message must be convertible  by
  json/write-str. Creates an topic exchange with default parameters for the
  given name. The topic is only created once for a given connection and name.
  Uses name as the routing key if no routing-key is given."
  ([name message]
   (publish name message {} name))
  ([name message options]
   (publish name message options name))
  ([exchange-name message options routing-key]
   (catcher/with-logging {:level :error}
     (memoized-create-exchange exchange-name)
     (logging/debug {:publish {:message message
                               :exchange exchange-name :routing-key routing-key}})
     (lb/publish (get-channel) exchange-name routing-key
                 (json/write-str message)
                 (conj
                   {:persistent true}
                   options
                   {:content-type "application/json"})))))

(defn listen
  "Listen to the message with (the routing key) name  usually in ad hoc
  fashion.  Listener is a function that takes one argument, the message. The
  message is a json compatible object.  The variant without specifying the
  queue name creates a temporary, exclusive and auto deleted queue. The variant
  specifying the queue name creates a durable queue."
  ([name receiver]
   (listen name receiver "" {:durable false :exclusive true :auto-delete true}))
  ([exchange-name receiver queue-name]
   (listen exchange-name receiver queue-name {}))
  ([exchange-name receiver qname options]
   (catcher/with-logging {:level :warn}
     (create-exchange exchange-name)
     (let [queue-name (:queue
                        (lq/declare (get-channel)
                                    qname
                                    (conj
                                      {:durable true :exclusive false :auto-delete false}
                                      options)))]
       (lq/bind (get-channel) queue-name exchange-name {:routing-key "#"})
       (future
         (logging/debug "subscribe: "  queue-name " to " receiver)
         (lcons/subscribe (get-channel) queue-name
                          (create-handler receiver)
                          {:auto-ack true}))))))

(defn check-connection
  "Checks if the internal channel is open and returns true in case."
  []
  (try
    (catcher/with-logging {:level :warn} (rmq/open? (get-channel)))
    (catch Exception _ false)
    ))

;### initialize ###############################################################
(defn initialize [new-conf]
  (logging/info [initialize new-conf])
  (reset! conf new-conf)
  (catcher/with-logging {:level :error}
    (connect (:connection @conf))
    (reset! logging-queue (lq/declare (get-channel)))
    (future
      (lcons/subscribe (get-channel) (:queue @logging-queue)
                       logging-receiver {:auto-ack false}))
    (logging/info "messaging initialized")
    ))



;### Debug ####################################################################
;(debug/debug-ns *ns*)
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)

