#!/usr/bin/env ruby
require 'yaml'
require 'fileutils'

### CIDER-CI Service Configuration ######################################

config = YAML.load_file 'config/config_default.yml'

# database
config['database']['user']= ENV['PGUSER']
config['database']['password']= ENV['PGPASSWORD']
config['database']['subname']= "//localhost:#{ENV['PGPORT']}/#{ENV['DATABASE_NAME']}"

# storage
config['services']['server']['http']['port']= Integer(ENV['SERVER_HTTP_PORT'])
config['services']['server']['http']['host']= 'localhost'
config['services']['server']['nrepl']['enabled']= false

# write config
File.open('config/config.yml','w') { |file| file.write config.to_yaml }

FileUtils.mkdir_p config['services']['server']['repositories']['path']

config['services']['server']['stores'].each do |store|
  FileUtils.mkdir_p store['file_path']
end
