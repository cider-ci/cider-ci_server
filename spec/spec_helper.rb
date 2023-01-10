require 'active_support/all'

require 'config/browser'
require 'config/database'
require 'config/web'
require 'faker'
require 'helpers/db'
require 'helpers/global'
require 'helpers/users'
require 'logger'
require 'pry'

RSpec.configure do |config|
  config.include Helpers::Global
  config.include Helpers::DB
  config.include Helpers::Users

  config.before :all do
    @spec_seed = \
      ENV['SPEC_SEED'].presence.try(:strip) || `git log -n1 --format=%T`.strip
    puts "SPEC_SEED #{@spec_seed} set env SPEC_SEED to force value"
    srand Integer(@spec_seed, 16)
  end
  config.after :all do
    puts "SPEC_SEED #{@spec_seed} set env SPEC_SEED to force value"
  end
end

