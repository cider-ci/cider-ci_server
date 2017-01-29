require 'spec_helper'
require 'pg_tasks'
require 'pry'

feature 'Session expiration', type: :feature do

  context 'Users admin and normin' do

    before :each do
      PgTasks.truncate_tables()
      create_default_users
    end

    context "The max_session_lifetime of every user is set to 15 seconds" do

      before :each do
        @users.update(max_session_lifetime: '15 seconds')
      end

      scenario "The the user will be signed out after 15 seconds " do

        visit '/cider-ci/ui2/'
        click_on 'Sign in with password'

        within ".sign-in-page" do |el|
          find('input#login').set 'normin'
          find('input#password').set 'secret'
          click_on 'Sign me in'
        end

        # normin is signed in
        expect(first(".navbar")).not_to have_content "Sign in with password"
        expect(first(".navbar .user")).to have_content "normin"

        # normin is still signed in after 5 seconds
        sleep(5)
        visit current_path
        expect(first(".navbar")).not_to have_content "Sign in with password"
        expect(first(".navbar .user")).to have_content "normin"

        # normin will be signed off after 15 seconds of the initial sign-in
        sleep(10)
        visit current_path
        expect(first(".navbar")).to have_content "Sign in with password"
        expect(first(".navbar .user")).not_to have_content "normin"
      end
    end


    context 'The session -> max_lifetime configuration is temporarily ' \
      'set to 15 seconds' do

      before :each do
        ci_config = begin
                      YAML.load_file('config/config.yml')
                    rescue
                      {}
                    end.with_indifferent_access
        ci_config.deep_merge!({session:{max_lifetime: '15 seconds'}})
        IO.write 'config/config.yml', ci_config.as_json.to_yaml
      end

      after :each do
        ci_config = begin
                      YAML.load_file('config/config.yml')
                    rescue
                      {}
                    end.with_indifferent_access
        ci_config.deep_merge!({session:{max_lifetime: '7 days'}})
        IO.write 'config/config.yml', ci_config.as_json.to_yaml
      end

      scenario "The the user will be signed out after 15 seconds " do

        visit '/cider-ci/ui2/'
        click_on 'Sign in with password'

        within ".sign-in-page" do |el|
          find('input#login').set 'normin'
          find('input#password').set 'secret'
          click_on 'Sign me in'
        end

        # normin is signed in
        expect(first(".navbar")).not_to have_content "Sign in with password"
        expect(first(".navbar .user")).to have_content "normin"

        # normin is still signed in after 5 seconds
        sleep(5)
        visit current_path
        expect(first(".navbar")).not_to have_content "Sign in with password"
        expect(first(".navbar .user")).to have_content "normin"

        # normin will be signed off after 15 seconds of the initial sign-in
        sleep(10)
        visit current_path
        expect(first(".navbar")).to have_content "Sign in with password"
        expect(first(".navbar .user")).not_to have_content "normin"
      end

    end

  end

end
