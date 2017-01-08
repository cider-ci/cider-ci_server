require 'spec_helper'
require 'pg_tasks'
require 'pry'

feature 'GitHub authentication' do

  def sign_in_as login
    wait_until 3 do
      visit current_path
      first('a,button', text: 'Sign in via GitHubMock').click rescue nil
    end
    wait_until 3 do
      first('a,button', text: "Sign in as #{login}").click rescue nil
    end
  end

  def signed_in_as! login
    wait_until 3 do
      page.has_content? "#{login}@GitHubMock"
    end
    expect(page).not_to have_content 'Sign in via GitHubMock'
  end


  before :each do
    ci_config = begin
                  YAML.load_file('config/config.yml')
                rescue
                  {}
                end.with_indifferent_access
    ci_config.deep_merge!(github_config.with_indifferent_access)
    IO.write 'config/config.yml', ci_config.as_json.to_yaml
  end

  after :each do
    ci_config = begin
                  YAML.load_file('config/config.yml')
                rescue
                  {}
                end.with_indifferent_access

  IO.write 'config/config.yml', \
    ci_config.except(:github_authtoken, :authentication_providers) \
    .as_json.to_yaml
  end

  before :each do
    PgTasks.truncate_tables
  end

  before :all do
    @users = database[:users]
    @email_addresses = database[:email_addresses]
  end

  describe 'test email sign-in strategy' do
    scenario 'adam can sign-in' do
      visit '/cider-ci/ui2/'
      sign_in_as 'adam'
      signed_in_as! 'adam'
    end
  end

  describe 'test org sign-in strategy' do
    scenario 'normin can sign-in' do
      visit '/cider-ci/ui2/'
      sign_in_as 'normin'
      signed_in_as! 'normin'
    end
  end

  describe 'test team sign-in strategy' do
    scenario 'tessa can sign-in' do
      visit '/cider-ci/ui2/'
      sign_in_as 'tessa'
      signed_in_as! 'tessa'
    end
  end

  describe 'test no matching sign-in strategy' do
    scenario 'silvan can not sign-in' do
      visit '/cider-ci/ui2/'
      sign_in_as 'silvan'
      expect(page).to have_content 'No sign in strategy succeeded!'
      visit '/cider-ci/ui2/'
      wait_until 3 do
        page.has_content? 'Sign in via GitHubMock'
      end
    end
  end

  describe 'test create attributes' do
    scenario 'adam is admin' do
      visit '/cider-ci/ui2/'
      sign_in_as 'adam'
      signed_in_as! 'adam'
      # adam is an admin
      expect(
        @users.where(login: 'adam@GitHubMock').first[:is_admin]
      ).to be true
    end
  end

  describe 'test update attributes' do
    scenario 'sign in as tessa, remove admin attribute, sign in again' do
      visit '/cider-ci/ui2/'
      sign_in_as 'tessa'
      signed_in_as! 'tessa'
      # tessa is admin
      expect(
        @users.where(login: 'tessa@GitHubMock').first[:is_admin]
      ).to be true
      # make tessa a non admin
      @users.update(is_admin: false)
      expect(
        @users.where(login: 'tessa@GitHubMock').first[:is_admin]
      ).to be false
      # sign in again
      click_on 'tessa@GitHubMock'
      click_on 'Sign out'
      sign_in_as 'tessa'
      # tessa is admin again
      expect(
        @users.where(login: 'tessa@GitHubMock').first[:is_admin]
      ).to be true
    end
  end


  describe 'test email-transfer and redirect to original url' do

    context 'user adam0 with email_address "adam.admin@example.com" ' do
      before :each do
        password_digest = '$2a$06$YXyaPR7IzANgRuOx5JJTzO8THVxfeQ8wVTB.NZJzySLP6Qg5v3zhW'
        adam_user_id = @users.insert(login: 'adam', is_admin: true, password_digest: password_digest)
        @email_address_id = @email_addresses.insert(user_id: adam_user_id, email_address: 'adam.admin@example.com')
      end

      scenario 'it works' do

        visit '/cider-ci/ui2/debug'
        sign_in_as 'adam'
        signed_in_as! 'adam'

        # the email_address has been transfered to the new account
        adam_ghm = @users.where(login: 'adam@GitHubMock').first
        expect(@email_addresses.count).to be== 1
        expect(
          @email_addresses.first[:user_id]
        ).to be== adam_ghm[:id]

        # we have been redirected back to the url where we clicked on sign in
        expect(current_path).to be== '/cider-ci/ui2/debug'

      end
    end
  end




  let :github_config do
    YAML.load <<-YML.strip_heredoc
      github_authtoken: the-global-github-auth-token

      authentication_providers:

        #  the GitHub provider MUST use the key `github`
        #  the "callback URL" of the application MUST read like the following
        #  http://YOUR-SERVER-NAME/cider-ci/ui/public/auth_provider/github/sign_in

        github:

          name: GitHubMock
          client_id: the GitHub OAuth client_id
          client_secret: the GitHub OAuth client_secret
          api_endpoint: |
            http://localhost:#{ENV['GITHUB_API_MOCK_PORT']}
          oauth_base_url: |
            http://localhost:#{ENV['GITHUB_API_MOCK_PORT']}/login/oauth

          ### sign-in_strategies ######################################################
          # first match will be used => order strategies with most permissive
          # properties first!
          #############################################################################

          sign_in_strategies:

            ### email-addresses #######################################################
            # * honors any verified (!) email addresses associated with the user
            # * does not require  any team or organization membership
            # * sign-in is faster compared to organization or team membership
            - type: email-addresses
              email_addresses:
                - adam.admin@example.com
              create_attributes:
                is_admin: true
                account_enabled: true
                password_sign_in_allowed: true
                max_session_lifetime: 7 days

            ### team-membership #######################################################
            # Example: members of the "Admin" group are automatically promoted to
            # to Cider-CI admins.
            - type: team-membership
              access_token: access-token of an owner with org:read scope
              organization_login: TestOrg
              team_name: Admins
              create_attributes:
                is_admin: true
                account_enabled: true
                password_sign_in_allowed: true
                max_session_lifetime: 3 days
              update_attributes:
                is_admin: true
                account_enabled: true

            ### organization-membership ###############################################
            # Members of the "TestOrg" organization can sign-in.
            - type: organization-membership
              organization_login: TestOrg
              create_attributes:
                is_admin: false
                account_enabled: true
                password_sign_in_allowed: false
                max_session_lifetime: 1 day
              update_attributes:
                account_enabled: true
    YML
  end

end


