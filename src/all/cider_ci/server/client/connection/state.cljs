(ns cider-ci.server.client.connection.state
  (:refer-clojure :exclude [str keyword])
  (:require
    [reagent.core :as reagent]
    )
  (:require-macros
    [reagent.ratom :as ratom :refer [reaction]]
    ))

(def requests* (reagent/atom {}))

(def socket* (reagent/atom
               {:msg_received_at (js/moment)
                :timestamped_at (js/moment)
                :msg_sent_at (js/moment)
                }))

(def socket-active?*
  (reaction
    (let [msg-received-at (:msg_received_at @socket*)
          msg-sent-at (:msg_sent_at @socket*)
          timestamped-at (:timestamped_at @socket*) ]
      (or (>= 1000 (Math/abs (- (.valueOf timestamped-at)
                                (.valueOf msg-received-at))))
          (>= 1000 (Math/abs (- (.valueOf timestamped-at)
                                (.valueOf msg-sent-at))))))))

(js/setInterval
  #(swap! socket*
          assoc :timestamped_at (js/moment))
  1000)
