require 'capybara/rspec'
require 'selenium-webdriver'

BROWSER_DOWNLOAD_DIR= File.absolute_path(File.expand_path(__FILE__)  + "/../../../tmp")

def http_port
  @port ||= Integer(ENV['CIDER_CI_TEST_SERVER_HTTP_PORT'].presence || 3320)
end

def http_host
  @host ||= ENV['CIDER_CI_TEST_SERVER_HTTP_HOST'].presence || 'localhost'
end

def http_base_url
  @http_base_url ||= "http://#{http_host}:#{http_port}"
end

def set_capybara_values
  Capybara.app_host = http_base_url
  Capybara.server_port = http_port
  Capybara.test_id = 'data-test-id'
end


firefox_bin_path = Pathname.new(`asdf where firefox`.strip).join('bin/firefox').expand_path.to_s
puts firefox_bin_path
Selenium::WebDriver::Firefox.path = firefox_bin_path

Capybara.register_driver :firefox do |app|
  capabilities = Selenium::WebDriver::Remote::Capabilities.firefox(
    # TODO: trust the cert used in container and remove this:
    acceptInsecureCerts: true
  )

  profile = Selenium::WebDriver::Firefox::Profile.new
  # TODO: configure language for locale testing
  # profile["intl.accept_languages"] = "en"
  #
  profile_config = {
    'browser.helperApps.neverAsk.saveToDisk' => 'image/jpeg,application/pdf,application/json',
    'browser.download.folderList' => 2, # custom location
    'browser.download.dir' => BROWSER_DOWNLOAD_DIR.to_s
  }
  profile_config.each { |k, v| profile[k] = v }

  opts = Selenium::WebDriver::Firefox::Options.new(
    binary: firefox_bin_path,
    profile: profile,
    log_level: :trace)

  # NOTE: good for local dev
  if ENV['CIDER_CI_TEST_HEADLESS'].present?
    opts.args << '--headless'
  end
  # opts.args << '--devtools' # NOTE: useful for local debug

  # driver = Selenium::WebDriver.for :firefox, options: opts
  # Capybara::Selenium::Driver.new(app, browser: browser, options: opts)
  Capybara::Selenium::Driver.new(
    app,
    browser: :firefox,
    options: opts,
    desired_capabilities: capabilities
  )
end


RSpec.configure do |config|
  set_capybara_values

  # Capybara.run_server = false
  Capybara.default_driver = :firefox
  Capybara.current_driver = :firefox


  config.before :all do
    set_capybara_values
  end

  config.before :each do |example|
    set_capybara_values
  end

  config.after(:each) do |example|
    unless example.exception.nil?
      take_screenshot screenshot_dir
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
