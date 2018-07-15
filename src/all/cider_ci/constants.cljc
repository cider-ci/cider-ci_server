; Copyright © 2013 - 2017 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.constants)

(def SESSION-COOKIE-KEY :cider-ci_session)

(def ANTI_CRSF_TOKEN_COOKIE_NAME "cider-ci-anti-csrf-token")

(def HTTP_UNSAVE_METHODS #{:delete :patch :post :put})
(def HTTP_SAVE_METHODS #{:get :head :options :trace})

(def STATES {:JOB '("passed" "executing" "pending" "aborting" "aborted" "defective" "failed"),
             :TASK '("aborted" "aborting" "defective" "executing" "failed" "passed" "pending"),
             :TRIAL '("aborted" "aborting" "defective" "dispatching" "executing" "failed" "passed" "pending"),
             :SCRIPT '("aborted" "defective" "executing" "failed" "passed" "pending" "skipped" "waiting"),
             :FINISHED '("aborted" "defective" "failed" "passed" "skipped") ,
             :IN_PROGRESS '("aborting" "dispatching" "executing")})

(def GRID-COLS 24)

(def utf8-times "\u00d7")

(def utf8-erase-to-right "\u2326")

(def utf8-narrow-no-break-space "\u202F")

(def WORKING-DIR 
  #?(:clj (System/getProperty "user.dir")
     :default nil))


