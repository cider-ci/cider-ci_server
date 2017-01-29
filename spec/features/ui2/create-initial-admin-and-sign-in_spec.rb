require 'spec_helper'
require 'pg_tasks'
require 'pry'

feature 'Initial admin', type: :feature do

  scenario "Create an initial admin and sign works " do

    PgTasks.truncate_tables()

    visit '/cider-ci/ui2/'

    #############################################################
    ### create initial admin and sign in
    #############################################################


    ### we haven been redirected to the create-initial-admin-page
    wait_until(3) do
      first(".create-initial-admin h1") \
        .try :has_content?, "Create an initial administrator user"
    end

    ### create the admin user

    within ".create-initial-admin"  do
      find('input#login').set 'admin'
      find('input#password').set 'secret'
      click_on 'Submit'
    end

    wait_until(3) do
      page.has_content? \
        "initial administrator user has been created"
    end

    ### we have been redirected to the sign-in-page

    wait_until(5) do
      first(".sign-in-page h1").try(:has_content?, "Sign in")
    end

    ### sign in

    within ".sign-in-page" do |el|
      find('input#login').set 'admin'
      find('input#password').set 'secret'
      click_on 'Sign me in'
    end

    ### we are redirected to the front page and we are signed in

    wait_until(3) do
      current_path == "/cider-ci/ui2/"
    end

    expect(first(".navbar .user")).to have_content "admin"

  end


  context "An admin already exists" do

    before :each do
      PgTasks.truncate_tables
      users = database[:users]
      users.insert(login: 'admin', is_admin: true)
    end

    scenario "It is not possible to create an initial admin" do

      visit '/cider-ci/ui2/create-admin'

      within ".create-initial-admin"  do
        find('input#login').set 'anotheradmin'
        find('input#password').set 'secret'
        click_on 'Submit'
      end

      within ".create-initial-admin"  do
        wait_until(3){ first(".alert")}
        expect(find(".alert")).to have_content "Creating an administrator user failed"
        expect(find(".alert")).to have_content "Conflict"
        expect(find(".alert")).to have_content "409"
      end

    end
  end

end
