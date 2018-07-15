(ns cider-ci.server.front.icons)

(def add [:i.fas.fa-plus-circle])
(def admin [:i.fas.fa-wrench])
(def commits [:i.fas.fa-code-branch])
(def delete [:i.fas.fa-times])
(def edit [:if.fas.fa-edit])
(def email-address [:i.fas.fa-at])
(def email-addresses [:i.fas.fa-at])
(def gpg-key [:i.fas.fa-key])
(def gpg-keys gpg-key)
(def home [:i.fas.fa-home])
(def project [:i.fas.fa-box])
(def projects [:i.fas.fa-boxes])
(def sign-in [:i.fas.fa-sign-in-alt])
(def sign-out [:i.fas.fa-sign-out-alt])
(def user [:i.fas.fa-user])
(def users [:i.fas.fa-users])

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
