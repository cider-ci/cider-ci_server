require 'spec_helper'
require 'shared-contexts/admin'
require 'shared-contexts/demo-project'
require 'pry'


feature 'Pushing a project', type: :feature do

  include_context :initial_admin
  include_context :sign_in_as_admin
  include_context :create_an_api_token
  include_context :demo_project

  scenario 'works and we can see the pushed commits'  do
    binding.pry
  end
end
