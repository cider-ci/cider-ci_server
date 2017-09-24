(ns cider-ci.server.client.connection.state
  (:refer-clojure :exclude [str keyword])
  (:require
   [reagent.core :as reagent]
   ))

(def requests* (reagent/atom {}))

(def socket* (reagent/atom {}))
