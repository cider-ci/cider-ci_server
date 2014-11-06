(ns cider-ci.utils.http_spec
  (:require 
    [clojure.tools.logging :as logging]
    [cider-ci.utils.http :as http]
    )
  (:use 
    [midje.sweet])
  )

(facts "http/build-url"  
       (facts "config without :ssl and without host"
              (let [config {:ssl nil
                            :host nil
                            :port "80"
                            :context "/context"
                            :sub_context "/subcontext"}]
                (fact (http/build-url config "/x") => ":80/context/subcontext/x"))))

(facts "http/build-url"  
       (facts "config without :ssl "
              (let [config {:ssl nil
                            :host "localhost"
                            :port "80"
                            :context "/context"
                            :sub_context "/subcontext"}]
                (fact (http/build-url config "/x") => "localhost:80/context/subcontext/x"))))

(facts "http/build-url"  
       (facts "config with :ssl true "
              (let [config {:ssl true
                            :host "localhost"
                            :port "443"
                            :context "/context"
                            :sub_context "/subcontext"}]
                (fact (http/build-url config "/x") => "https://localhost:443/context/subcontext/x"))))
