require 'spec_helper'
require 'shared-contexts/admin'
require 'shared-contexts/demo_project'
require 'shared-contexts/create_an_api_token'
require 'pry'


feature 'Pushing a project', type: :feature do

  include_context :initial_admin
  include_context :sign_in_as_admin
  include_context :create_an_api_token
  include_context :demo_project

  scenario 'works and we can see the pushed commits very quickly thanks to events and sockets'  do
    DemoProject.push_commit '3ec64b0', @api_token_secret
    wait_until 5 do
      page.has_content? 'Add cron rerun demo'
    end 
    DemoProject.push_commit '5b1bf07', @api_token_secret
    wait_until 5 do
      page.has_content? 'Add cron triggered demo'
    end
  end
end
