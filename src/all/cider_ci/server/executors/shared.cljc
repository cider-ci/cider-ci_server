; Copyright © 2013 - 2018 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.
 
(ns cider-ci.server.executors.shared
  (:refer-clojure :exclude [str keyword])
  (:require
    [cider-ci.utils.core :refer [keyword str presence]]
    [cider-ci.server.paths :refer [path]]

    ))


(def admin-action-accepted-keys
  [:description
   :enabled
   :id
   :name
   :public_key])
