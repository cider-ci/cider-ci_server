require 'spec_helper'

feature 'Admin manages Repositories', type: :feature do

  before :each do
    PgTasks.truncate_tables()
    create_default_users
  end

  scenario 'Create a repository, editing and deleting it' do
    sign_in_as 'admin'
    visit '/'
    click_on 'Projects'
    wait_until(5) { page.has_content? 'Add a new project'}
    click_on 'Add a new project'
    find('input#git_url').set 'https://github.com/cider-ci/cider-ci_demo-project-bash.git'
    find('input#name').set 'TestRepo'
    find('input#name').set 'TestRepo'
    click_on 'Submit'
    wait_until(5) { page.has_content?  /Project\s+"TestRepo"/ }
    wait_until { page.has_content?  /Edit/ }
    click_on 'Edit'
    find('input#name').set 'UpdatedName'
    click_on 'Submit'
    wait_until(5) { page.has_content?  /Project\s+"UpdatedName"/ }
    click_on 'Delete'
    wait_until(5) { page.has_content?  /Add a new project/ }
    expect(find(".table-projects")).not_to have_content "UpdatedName"
  end
end

