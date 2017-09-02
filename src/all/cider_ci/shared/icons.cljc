; Copyright © 2013 - 2017 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.shared.icons
  (:refer-clojure :exclude [str keyword])
  (:require [cider-ci.utils.core :refer [keyword str]]))

(defn state-icon [state]
  (case (keyword state)
    :aborted [:i.fa.fa-fw.fa-power-off.fa-rotate-180]
    :aborting [:i.fa.fa-fw.fa-power-off.fa-spin-cc]
    :defective [:i.fa.fa-fw.flash]
    :dispatching [:i.fa.fa­fw.fa-spinner.fa-pulse]
    :executing [:i.fa.fa­fw.fa-cog.fa-spin]
    :failed [:i.fa.fa-fw.fa-times]
    :passed [:i.fa.fa-fw.fa-check]
    :pending [:i.fa.fa­fw.fa-spinner.fa-pulse]
    ))
