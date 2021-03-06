require 'capybara/rspec'
require 'capybara/poltergeist'
require 'selenium-webdriver'
require 'json_roa/client'

def port
  @port ||= Integer(ENV['SERVER_HTTP_PORT'].presence || 8881)
end

def base_url
  @base_url ||= "http://localhost:#{port}"
end

def json_roa_client(&block)
  JSON_ROA::Client.connect \
    base_url, raise_error: false, &block
end

def plain_faraday_json_client
  @plain_faraday_json_client ||= Faraday.new(
    url: base_url,
    headers: { accept: 'application/json' }) do |conn|
      conn.adapter Faraday.default_adapter
      conn.response :json, content_type: /\bjson$/
    end
end

def set_capybara_values
  Capybara.app_host = base_url
  Capybara.server_port = port
end

def set_browser(example)
  Capybara.current_driver = \
    begin
      ENV['CAPYBARA_DRIVER'].presence.try(:to_sym) \
          || example.metadata[:driver] \
          || :selenium
    rescue
      :selenium
    end
end

RSpec.configure do |config|
  Capybara.current_driver = :selenium
  set_capybara_values

  if ENV['FIREFOX_ESR_PATH'].present?
    Selenium::WebDriver::Firefox.path = ENV['FIREFOX_ESR_PATH']
  end

  Capybara.register_driver :poltergeist do |app|
    Capybara::Poltergeist::Driver.new(app, js_errors: false)
  end

  config.before :all do
    set_capybara_values
  end

  config.before :each do |example|
    set_capybara_values
    set_browser example
  end
end
