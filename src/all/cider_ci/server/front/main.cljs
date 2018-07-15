(ns cider-ci.server.front.main
  (:refer-clojure :exclude [str keyword])
  (:require-macros
    [reagent.ratom :as ratom :refer [reaction]]
    )
  (:require
    [cider-ci.server.front.html :as html]
    [cider-ci.server.paths :as paths]
    [cider-ci.server.routes :as routes]
    [cider-ci.server.socket.front :as socket]
    [cider-ci.utils.core :refer [keyword str presence]]

    [clojure.string :as str]
    [clojure.pprint :refer [pprint]]

    [reagent.core :as reagent]
    [accountant.core :as accountant]
    ))

(defn init! []
  (routes/init)
  (html/mount)
  (socket/init))
