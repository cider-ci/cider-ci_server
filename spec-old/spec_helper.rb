require 'active_support/all'

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

  config.after(:each) do |example|
    # cleanup
    take_screenshot unless example.exception.nil?
  end

  def take_screenshot(screenshot_dir = nil, name = nil)
    screenshot_dir ||= File.join(Dir.pwd, 'tmp')
    begin
      Dir.mkdir screenshot_dir
    rescue
      nil
    end
    name ||= "screenshot_#{Time.now.iso8601.tr(':', '-')}.png"
    path = File.join(screenshot_dir, name)
    case Capybara.current_driver
    when :selenium, :selenium_chrome
      begin
        page.driver.browser.save_screenshot(path)
      rescue
        nil
      end
    when :poltergeist
      begin
        page.driver.render(path, full: true)
      rescue
        nil
      end
    else
      logger = Logger.new(STDOUT)
      logger.warn 'Taking screenshots is not implemented for ' \
        "#{Capybara.current_driver}."
    end
  end
end
