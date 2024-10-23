#!/usr/bin/env ruby
require 'yaml'

### CIDER-CI Service Configuration ######################################

config = YAML.load_file 'resources_dev/config_defaults.yml'

# database
config['database']['user']= ENV['PGUSER']
config['database']['password']= ENV['PGPASSWORD']
config['database']['subname']= "//localhost:#{ENV['PGPORT']}/#{ENV['CIDER_CI_TEST_DATABASE']}"

# storage
config['services']['server']['http']['port']= Integer(ENV['CIDER_CI_TEST_SERVER_HTTP_PORT'])
config['services']['server']['http']['host']= 'localhost'
config['services']['server']['nrepl']['enabled']= false

# write config
File.open('config/config.yml','w') { |file| file.write config.to_yaml }
