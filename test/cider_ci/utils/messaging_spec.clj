(ns cider-ci.utils.messaging_spec
  (:require 
    [langohr.http]
    [clojure.tools.logging :as logging]
    )
  (:use 
    [cider-ci.utils.messaging]
    [midje.sweet])
  )


(defmacro with-connection [& body]
  `(try
     (initialize {})
     ~@body
     (finally
       (disconnect))))


(facts "full message cycle using the high-level functions listen and publish" 
       (with-connection
         (let [last-message (atom nil)
               listener (fn [message] 
                          (logging/info "received test message" message)
                          (reset! last-message message))]
           (listen "test" listener "test.queue" )
           (publish "test"  {:x 42})
           (deref  (future (loop [] 
                             (Thread/sleep 100)
                             (when-not @last-message (recur)))) 1000 nil) 
           (fact @last-message => {:x 42})
           )))

(facts "full message cycle using the high-level functions listen (short form) and publish" 
       (with-connection
         (let [last-message (atom nil)
               listener (fn [message] 
                          (logging/info "received test message" message)
                          (reset! last-message message))]
           (listen "test" listener)
           (publish "test"  {:x 42})
           (deref  (future (loop [] 
                             (Thread/sleep 100)
                             (when-not @last-message (recur)))) 1000 nil) 
           (fact @last-message => {:x 42})
           )))
