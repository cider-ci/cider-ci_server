; Copyright © 2013 - 2018 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.server.resources.commits.shared
  (:refer-clojure :exclude [str keyword])
  (:require
    [cider-ci.utils.core :refer [keyword str presence]]))


(def default-query-parameters 
  {:project-name nil
   :project-name-as-regex true

   :branch-name nil
   :branch-name-as-regex false

   :email nil
   :email-as-regex false

   :git-ref nil
   :git-ref-as-regex false

   :heads-only true
   :my-commits false

   :page 1 
   :per-page 12})
