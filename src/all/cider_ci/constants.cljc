; Copyright © 2013 - 2017 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.

(ns cider-ci.constants)

(def SESSION-COOKIE-KEY :cider-ci_services-session)

(def STATES {:JOB '("passed" "executing" "pending" "aborting" "aborted" "defective" "failed"),
             :TASK '("aborted" "aborting" "defective" "executing" "failed" "passed" "pending"),
             :TRIAL '("aborted" "aborting" "defective" "dispatching" "executing" "failed" "passed" "pending"),
             :SCRIPT '("aborted" "defective" "executing" "failed" "passed" "pending" "skipped" "waiting"),
             :FINISHED '("aborted" "defective" "failed" "passed" "skipped") ,
             :IN_PROGRESS '("aborting" "dispatching" "executing")})

(def GRID-COLS 24)
