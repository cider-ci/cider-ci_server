require 'capybara/rspec'
require 'selenium-webdriver'
require 'json_roa/client'

def http_port
  @http_port ||= Integer(ENV['SERVER_HTTP_PORT'].presence || 8881)
end

def http_base_url
  @http_base_url ||= "http://localhost:#{http_port}"
end

def json_roa_client(&block)
  JSON_ROA::Client.connect \
    http_base_url, raise_error: false, &block
end

def plain_faraday_json_client
  @plain_faraday_json_client ||= Faraday.new(
    url: http_base_url,
    headers: { accept: 'application/json' }) do |conn|
      conn.adapter Faraday.default_adapter
      conn.response :json, content_type: /\bjson$/
    end
end


