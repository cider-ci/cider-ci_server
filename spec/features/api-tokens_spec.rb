require 'spec_helper'
require 'shared-contexts/create_an_api_token'
require 'shared-contexts/admin'
require 'pry'



feature 'API-Tokens', type: :feature do

  include_context :initial_admin
  include_context :sign_in_as_admin
  include_context :create_an_api_token

  scenario 'create an api token' do

    expect(@api_token_secret).to be
    
  end

end

