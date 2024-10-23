require 'active_support/all'

require 'config/database'
require 'config/web'
require 'config/browser'
require 'faker'
require 'helpers/global'
require 'helpers/users'
require 'logger'
require 'pry'

RSpec.configure do |config|
  config.include Helpers::Global
  config.include Helpers::Users

  config.before :all do
    @spec_seed = \
      ENV['SPEC_SEED'].presence.try(:strip) || `git log -n1 --format=%T`.strip
    puts "SPEC_SEED #{@spec_seed} set env SPEC_SEED to force value"
    srand Integer(@spec_seed, 16)
  end


  def pry_on_exception?
    begin
      ENV['PRY_ON_EXCEPTION'].presence.try(:to_yaml) || false
    rescue
      false
    end
  end

  config.after(:example) do |example|
    if not ENV['CIDER_CI_TRIAL_ID'].present? and pry_on_exception?
      unless example.exception.nil?
        binding.pry if example.exception
      end
    end
  end


  config.after :all do
    puts "SPEC_SEED #{@spec_seed} set env SPEC_SEED to force value"
  end

end
