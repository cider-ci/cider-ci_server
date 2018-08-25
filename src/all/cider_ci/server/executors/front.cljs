; Copyright © 2013 - 2018 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.  

(ns cider-ci.server.executors.front
  (:refer-clojure :exclude [str keyword])
  (:require
    [cider-ci.utils.core :refer [keyword str]]
    [cider-ci.utils.url.query-params :refer [encode-query-params]]
    [cider-ci.server.executors.front.index :as executors-index]
    [cider-ci.server.executors.front.add :as executors-add]
    [cider-ci.server.executors.front.show :as show]
    [bidi.verbose :refer [branch param leaf]]
    [bidi.bidi :as bidi :refer [path-for match-route]]
    ))

(def routes-resolve-table
  {:executor show/page
   :executors executors-index/page
   :executors-add executors-add/page})

