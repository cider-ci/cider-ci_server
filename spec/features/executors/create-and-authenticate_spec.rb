require 'spec_helper'
require 'pg_tasks'

feature 'Create and authenticate an executor' do
  before :each do
    create_default_users
  end

  scenario " Create an executor with predefined id, and token
      via REST HTTP PUT as the system-administrator
      and check authentication as this executor " do
    name = 'Test-Executor'
    id = '88438012-ee85-418e-8988-bbd3f5ca12d3'
    token = 'ATopSecretTokenForTheTestExecutor'

    conn = plain_faraday_json_client
    # we send json
    conn.headers['Content-Type'] = 'application/json'
    # authenticate as system-admin
    conn.headers['Authorization'] = 'token master-secret'

    # check that we are authenticated as the system-admin
    authenticated_entity_response = conn.get '/cider-ci/authenticated-entity'
    expect(authenticated_entity_response.status).to be == 200
    expect(authenticated_entity_response.body['authenticated-entity']['type']).to be == 'system-admin'

    # create an executor
    create_response = conn.put "/cider-ci/executors/#{id}",
                               { name: name, token: token }.to_json
    # make sure this succeeded
    expect(create_response.status).to be == 201
    # the token is returned on create
    expect(create_response.body['token']).to be == token

    # fetch the executor via the api
    get_executor_response = conn.get("/cider-ci/executors/#{id}")
    expect(get_executor_response.status).to be == 200

    # now let's authenticate as an executer and check this does work
    conn.headers['Authorization'] = "token #{token}"
    authenticated_entity_response = conn.get '/cider-ci/authenticated-entity'
    expect(authenticated_entity_response.status).to be == 200
    expect(authenticated_entity_response.body['authenticated-entity']['type']).to be == 'executor'
  end

  context 'Signed in admin' do
    before :each do
      # sign in #################################################################
      visit '/cider-ci/'
      click_on 'Sign in with password'
      within '.sign-in-page' do
        find('input#login').set 'admin'
        find('input#password').set 'secret'
        click_on 'Sign me in'
      end
      wait_until 5 do
        first('.navbar .user').try(:has_content?, 'admin')
      end
    end

    scenario " Create an executor via REST HTTP POST
      as a regular administrator using an API Token " do
      # create an admin API-Token ###############################################
      click_on 'UI2'
      click_on 'API-Tokens'
      click_on 'Create'
      find('input#description').set 'Test admin API-Token'
      find('input#scope_admin_write').click
      within '.scope-presets' do
        click_on 'admin'
      end
      click_on 'Create'
      wait_until 5 do
        page.has_content? 'A new API-token has been created!'
      end
      api_token = find('.modal .token').text.strip
      click_on 'Continue'

      # set up and check API connection with the token ##########################
      conn = plain_faraday_json_client
      # we send json
      conn.headers['Content-Type'] = 'application/json'
      # authenticate as system-admin
      conn.headers['Authorization'] = "token #{api_token}"
      authenticated_entity_response = conn.get '/cider-ci/authenticated-entity'
      expect(authenticated_entity_response.status).to be == 200
      expect(authenticated_entity_response.body['authenticated-entity']['type']).to be == 'user'
      expect(authenticated_entity_response.body['authenticated-entity']['scope_admin_write']).to be == true

      # create an executor
      create_response = conn.post '/cider-ci/executors/',
                                  { name: 'Test Exececutor' }.to_json
      # make sure this succeeded
      expect(create_response.status).to be == 201
      # note the created token
      executor_token = create_response.body['token']

      # authenticate as the executer and check this does work
      conn.headers['Authorization'] = "token #{executor_token}"
      authenticated_entity_response = conn.get '/cider-ci/authenticated-entity'
      expect(authenticated_entity_response.status).to be == 200
      expect(authenticated_entity_response.body['authenticated-entity']['type']).to be == 'executor'
    end

    scenario 'Manage an executor via the UI' do
      # create an executor ######################################################
      visit '/cider-ci/executors/'
      click_on 'Add'
      find('input#name').set 'test-executor'
      click_on 'Add'
      executor_token = find('.modal .token').text.strip
      click_on 'Continue'

      # check that it is possible to sign in as this executor
      conn = plain_faraday_json_client
      conn.headers['Content-Type'] = 'application/json'
      conn.headers['Authorization'] = "token #{executor_token}"
      authenticated_entity_response = conn.get '/cider-ci/authenticated-entity'
      expect(authenticated_entity_response.status).to be == 200
      expect(authenticated_entity_response.body['authenticated-entity']['type']).to be == 'executor'

      # edit the executor, i.e. update the token ################################
      updated_token = 'ATopSecretTokenForTheTestExecutor'
      click_on 'Edit'
      wait_until(5) { !first('.modal') }
      find('input#token').set updated_token
      click_on 'Update'
      wait_until(5) { !first('.modal') }
      wait_until { page.has_content? 'test-executor' }

      # now we can't use the old token anymore
      authenticated_entity_response = conn.get '/cider-ci/authenticated-entity'
      expect(authenticated_entity_response.body['authenticated-entity']['type']).not_to be == 'executor'

      # but we can use the new one
      conn.headers['Authorization'] = "token #{updated_token}"
      authenticated_entity_response = conn.get '/cider-ci/authenticated-entity'
      expect(authenticated_entity_response.body['authenticated-entity']['type']).to be == 'executor'

      # delete the executor
      click_on 'Delete'
      wait_until { page.has_content? 'Executors' }
      wait_until { !page.has_content? 'test-executor' }

      # also executor auth fails because the executor has been removed
      # we better check this since we cache executors in RAM too
      conn.headers['Authorization'] = "token #{updated_token}"
      authenticated_entity_response = conn.get '/cider-ci/authenticated-entity'
      expect(authenticated_entity_response.body['authenticated-entity']['type']).not_to be == 'executor'
    end
  end

  context 'Signed in normin (no admin)' do
    before :each do
      # sign in #################################################################
      visit '/cider-ci/'
      click_on 'Sign in with password'
      within '.sign-in-page' do
        find('input#login').set 'normin'
        find('input#password').set 'secret'
        click_on 'Sign me in'
      end
      wait_until 5 do
        first('.navbar .user').try(:has_content?, 'normin')
      end
    end

    scenario " Try to create an executor via REST HTTP POST
      as a non administrator user using an API Token results
      in 403 Forbitten " do
      # create an admin API-Token ###############################################
      click_on 'UI2'
      click_on 'API-Tokens'
      click_on 'Create'
      find('input#description').set 'Test admin API-Token'
      find('input#scope_admin_write').click
      within '.scope-presets' do
        click_on 'admin'
      end
      click_on 'Create'
      wait_until 5 do
        page.has_content? 'A new API-token has been created!'
      end
      api_token = find('.modal .token').text.strip
      click_on 'Continue'

      # set up and check API connection with the token ##########################
      conn = plain_faraday_json_client
      # we send json
      conn.headers['Content-Type'] = 'application/json'
      # authenticate as system-admin
      conn.headers['Authorization'] = "token #{api_token}"
      authenticated_entity_response = conn.get '/cider-ci/authenticated-entity'
      expect(authenticated_entity_response.status).to be == 200
      expect(authenticated_entity_response.body['authenticated-entity']['type']).to be == 'user'
      expect(authenticated_entity_response.body['authenticated-entity']['scope_admin_write']).to be == false

      # create an executor
      create_response = conn.post '/cider-ci/executors/',
                                  { name: 'Test Exececutor' }.to_json
      # make sure this is forbidden
      expect(create_response.status).to be == 403
      # note the created token
    end
  end
end
