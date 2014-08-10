(ns cider-ci.api.scratch
  (:require 
    [cider-ci.utils.debug :as debug]
    [cider-ci.utils.exception :as exception]
    [clojure.tools.logging :as logging]
    [clj-logging-config.log4j :as logging-config]
    )
  (:import 
    [bcrypt_jruby BCrypt]
    )
  )

;(BCrypt/checkpw "test" "$2a$10$3fEroReJRuOkrifDtNLpU.o07xL7o6eqMA.newOwB1yYhhqpzBo1O")

