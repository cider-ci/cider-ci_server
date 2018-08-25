require 'spec_helper'

shared_context :initial_admin do
  before do 
    visit '/'
    # we get redirected to the initial admin because there are no admins yet
    expect(page).to have_content  "Initial Admin"
    # we create the initial admin
    fill_in 'primary_email_address', with: 'admin@example.com'
    fill_in 'password', with: 'password'
    click_on 'Create'
  end
end

shared_context :sign_in_as_admin do
  before do
    visit '/'
    # we sign-in as the admin
    click_on 'Sign in'
    fill_in 'email', with: 'admin@example.com'
    click_on 'Continue'
    fill_in 'password', with: 'password'
    click_on 'Sign in'
    # we are signed-in
    expect(page).to have_content 'Sign out'
  end
end


