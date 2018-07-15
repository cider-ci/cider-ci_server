require 'spec_helper'
require 'shared-contexts/admin'
require 'pry'

feature 'Initial admin', type: :feature do

  include_context :initial_admin
  include_context :sign_in_as_admin

  scenario 'Create an initial admin and sign works ' do


    # the authentication method is session
    visit '/auth/info'

    wait_until {page.has_content? /authentication-method.+session/}

    click_on 'Sign out'

    # we are signed-out
    expect(page).to have_content 'Sign in'


  end
end
