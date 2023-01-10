require 'pry'
require 'config/web'
require 'capybara/rspec'
require 'selenium-webdriver'

BROWSER_DOWNLOAD_DIR= File.absolute_path(File.expand_path(__FILE__)  + "/../../../tmp")

def on_ci?
  ENV['CIDER_CI_TRIAL_ID'].presence && true || false
end

firefox_bin_path = Pathname.new(`asdf where firefox`.strip).join('bin/firefox').expand_path.to_s
Selenium::WebDriver::Firefox.path = firefox_bin_path


Capybara.register_driver :firefox do |app|

  profile = Selenium::WebDriver::Firefox::Profile.new
  profile_config = {
    'browser.helperApps.neverAsk.saveToDisk' => 'image/jpeg,application/pdf,application/json',
    'browser.download.folderList' => 2, # custom location
    # 'browser.download.dir' => BROWSER_DOWNLOAD_DIR.to_s
  }
  profile_config.each { |k, v| profile[k] = v }

  opts = Selenium::WebDriver::Firefox::Options.new(
    binary: firefox_bin_path,
    profile: profile,
    log_level: :trace)

  opts.args << '--devtools' unless on_ci?

  Capybara::Selenium::Driver.new(app, browser: :firefox, options: opts)
end

def set_capybara_values
  Capybara.app_host = http_base_url
  Capybara.server_port = http_port
  Capybara.default_driver = :firefox
  Capybara.current_driver = :firefox
end

RSpec.configure do |config|
  set_capybara_values
  config.before :all do
    set_capybara_values
  end
  config.before :each do |example|
    set_capybara_values
  end

  config.after(:each) do |example|
    unless example.exception.nil?
      take_screenshot screenshot_dir
      unless on_ci?
        binding.pry
      end
    end
  end

  config.before :all do
    FileUtils.remove_dir(screenshot_dir, force: true)
    FileUtils.mkdir_p(screenshot_dir)
  end

  def screenshot_dir
    Pathname(BROWSER_DOWNLOAD_DIR).join('screenshots')
  end

  def take_screenshot(screenshot_dir = nil, name = nil)
    name ||= "#{Time.now.iso8601.tr(':', '-')}.png"
    path = screenshot_dir.join(name)
    case Capybara.current_driver
    when :firefox
      page.driver.browser.save_screenshot(path) rescue nil
    else
      Logger.warn "Taking screenshots is not implemented for \
              #{Capybara.current_driver}."
    end
  end
end
