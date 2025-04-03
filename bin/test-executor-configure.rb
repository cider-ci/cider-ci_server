#!/usr/bin/env ruby

require 'yaml'
require 'active_support/all'

File.open('executor-traits.yml','w') { |file| file.write ["Bash", "Git"].to_yaml }

config = YAML.load_file "tmp/executor/config.yml"
config['basic_auth']['password']= 'TestExecutor1234'
config['http']['enabled']= true
if executor_http_port = ENV['EXECUTOR_HTTP_PORT'].presence
  config['http']['port']= Integer( executor_http_port)
else
  config['http']['enabled']= false
end
if rv_http_port = ENV['CIDER_CI_TEST_RV_HTTP_PORT'].presence
  config['server_base_url']= ("http://localhost:" + ENV['CIDER_CI_TEST_RV_HTTP_PORT'])
end
if nrepl_port = ENV['EXECUTOR_NREPL_PORT'].presence
  config['nrepl']['enabled']=true
  config['nrepl']['port']= Integer(nrepl_port)
end
config['max_load']= 2
File.open('tmp/executor/config.yml','w') { |file| file.write config.to_yaml }
