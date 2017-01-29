require 'active_support/all'
ENV['RAILS_ENV'] = ENV['RAILS_ENV'].presence || 'test'

require 'faker'
require 'config/bundle'
require 'config/rails'
require 'config/database'
require 'config/web'
require 'rspec-rails'

require 'helpers/global'
require 'helpers/db'
require 'helpers/users'

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

  config.after(:each) do |example|
    # cleanup
    take_screenshot unless example.exception.nil?
  end

  def take_screenshot(screenshot_dir = nil, name = nil)
    screenshot_dir ||= File.join(Dir.pwd, 'tmp')
    Dir.mkdir screenshot_dir rescue nil
    name ||= "screenshot_#{Time.now.iso8601.gsub(/:/, '-')}.png"
    path = File.join(screenshot_dir, name)
    case Capybara.current_driver
    when :selenium, :selenium_chrome
      page.driver.browser.save_screenshot(path) rescue nil
    when :poltergeist
      page.driver.render(path, full: true) rescue nil
    else
      Rails.logger.warn 'Taking screenshots is not implemented for ' \
        "#{Capybara.current_driver}."
    end
  end

end
