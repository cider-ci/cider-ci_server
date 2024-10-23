require 'capybara/rspec'
require 'json_roa/client'

def port
  @port ||= Integer(ENV['CIDER_CI_TEST_SERVER_HTTP_PORT'].presence || 3320)
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


