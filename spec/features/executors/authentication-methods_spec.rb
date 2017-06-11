require 'spec_helper'
require 'pg_tasks'

feature 'Executor authentication methods' do
  before :each do
    create_default_users
  end

  context 'An existing Executor' do
    let(:name) { 'Test-Executor' }
    let(:id) { '88438012-ee85-418e-8988-bbd3f5ca12d3' }
    let(:token) { 'ATopSecretTokenForTheTestExecutor' }
    let :conn do
      conn = plain_faraday_json_client
      conn.headers['Content-Type'] = 'application/json'
      conn.headers['Authorization'] = 'token master-secret'
      conn
    end
    let :executor_conn do
      conn = plain_faraday_json_client
      conn.headers['Content-Type'] = 'application/json'
      conn
    end

    before :each do
      create_response = conn.put "/cider-ci/executors/#{id}",
                                 { name: name, token: token }.to_json
      expect(create_response.status).to be == 201
      expect(create_response.body['token']).to be == token
    end

    scenario 'Authenticate via Token-Auth ' do
      executor_conn.headers['Authorization'] = "token #{token}"
      authenticated_entity_response = executor_conn.get '/cider-ci/authenticated-entity'
      expect(authenticated_entity_response.status).to be == 200
      expect(authenticated_entity_response.body['authenticated-entity']['type']).to be == 'executor'
    end

    scenario 'Authenticate via Basic-Auth with the token as username only' do
      executor_conn.basic_auth(token, nil)
      authenticated_entity_response = executor_conn.get '/cider-ci/authenticated-entity'
      expect(authenticated_entity_response.status).to be == 200
      expect(authenticated_entity_response.body['authenticated-entity']['type']).to be == 'executor'
    end

    scenario 'Authenticate via Basic-Auth with the token as password' do
      executor_conn.basic_auth(name, token)
      authenticated_entity_response = executor_conn.get '/cider-ci/authenticated-entity'
      expect(authenticated_entity_response.status).to be == 200
      expect(authenticated_entity_response.body['authenticated-entity']['type']).to be == 'executor'
    end
  end
end
