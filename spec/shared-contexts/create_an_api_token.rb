require 'spec_helper'

shared_context :create_an_api_token do

  before do
    visit '/'
    click_on_first 'admin@example.com'
    click_on_first 'API-Tokens'
    click_on_first 'Add API-Token'

    fill_in 'description', with: 'My API Token'
    check 'admin write'
    click_on 'never'
    wait_until do
      not first("button", text: "Create API-Token").disabled?
    end
    sleep 3 # WTF
    click_on 'Create API-Token'
    wait_until do
      @api_token_secret = find(".token-secret").text.presence
    end
    click_on 'Continue'
  end

end
