(ns cider-ci.messaging.core
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
    [cider-ci.utils.with :as with]
    [cider-ci.utils.json-protocols]
    ) 
  (:import 
    [java.util.concurrent Executors]
    )) 


;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)


(declare 
  ch
  connect
  )


; ### initialization and configuration ######################################

(defonce conf (atom {}))

(defn initialize [new-conf]
  (logging/info [initialize new-conf])
  (reset! conf new-conf)
  (with/logging

    (connect (:connection @conf))

    (doseq [exchange (:exchanges @conf)]
      (le/declare @ch (:name exchange)  
                  (:type exchange) 
                  :durable (:durable exchange)))

    (logging/info "messaging initialized")
    ))


;### connection handling ######################################################

(defonce conn (atom nil))
(defonce ch (atom nil))

(defn get-channel []
  (or @ch
      (throw (IllegalStateException. 
               "Messaging channel ist not initialized."))))

(defn connect [conn-conf]
  (logging/debug [connect conn-conf])
  (reset! conn 
          (rmq/connect
            {:executor (Executors/newFixedThreadPool 4)
             :vhost (:vhost conn-conf)
             :host (:host conn-conf)
             :username (:username conn-conf)
             :password (:password conn-conf)
             }))
  (reset! ch
          (lch/open @conn)
          ))


;### utils ####################################################################

(defonce publish-event-last-args (atom nil))
(defn publish-event [exchange-name routing-key event]
  (let [args [exchange-name routing-key event]]
    (logging/debug publish-event args)
    (reset! publish-event-last-args  args)
    (with/logging 
      (lb/publish @ch exchange-name routing-key
                  (json/write-str event)
                  :content-type "application/json"
                  :persistent true
                  ))))
;(apply publish-event @publish-event-last-args)

; TODO add routing key option
(defn subscribe-to-queue [queue-name handler]
  (logging/debug [subscribe-to-queue queue-name handler])
  (lcons/subscribe 
    (get-channel) queue-name
    (fn [ch metadata ^bytes payload]
      (with/logging-and-suppress
        (logging/info "EVENT" 
                      (select-keys metadata [:type :exchange])
                      (String. payload "UTF-8"))
        (let [message (clojure.walk/keywordize-keys 
                        (json/read-str (String. payload "UTF-8")))]
          (handler metadata message))))
    :auto-ack true))


