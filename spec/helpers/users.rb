module Helpers
  module Users
    extend self

    def create_default_users
      password_digest = '$2a$06$YXyaPR7IzANgRuOx5JJTzO8THVxfeQ8wVTB.NZJzySLP6Qg5v3zhW'
      @users = database[:users]
      @users.insert(login: 'normin', is_admin: false, password_digest: password_digest)
      @users.insert(login: 'admin', is_admin: true, password_digest: password_digest)
    end

    def sign_in_as user
      visit '/cider-ci/ui2/'
      click_on 'Sign in with password'
      within ".sign-in-page" do |el|
        find('input#login').set user
        find('input#password').set 'secret'
        click_on 'Sign me in'
      end
      expect(first(".navbar .user")).to have_content user
    end

  end
end
