require 'spec_helper'

feature 'Admin manages Repositories', type: :feature do
  before :each do
    PgTasks.truncate_tables
    create_default_users
  end

  scenario 'Create a repository, editing and deleting it' do
    sign_in_as 'admin'
    visit '/cider-ci/'
    click_on 'Projects'
    wait_until(5) { page.has_content? 'Add a new project' }
    click_on 'Add a new project'
    find('input#git_url').set 'https://github.com/cider-ci/cider-ci_demo-project-bash.git'
    find('input#name').set 'TestRepo'
    find('input#name').set 'TestRepo'
    click_on 'Submit'
    wait_until(5) { page.has_content? /Project\s+"TestRepo"/ }
    wait_until { page.has_content? /Edit/ }
    click_on 'Edit'
    find('input#name').set 'UpdatedName'
    click_on 'Submit'
    wait_until(5) { page.has_content? /Project\s+"UpdatedName"/ }
    click_on 'Delete'
    wait_until(5) { page.has_content? /Add a new project/ }
    expect(find('.table-projects')).not_to have_content 'UpdatedName'
  end

  scenario 'Try to add an repository as a non-admin user' do
    sign_in_as 'normin'
    visit '/cider-ci/'
    click_on 'Projects'
    wait_until(5) { page.has_content? 'Add a new project' }
    # however it is disabled because normin is not an admin
    expect(first('.disabled', text: 'Add a new project')).to be
    # we can easily circumvent the disabled link by visiting the route
    visit '/cider-ci/repositories/projects/new'
    find('input#git_url').set 'https://github.com/cider-ci/cider-ci_demo-project-bash.git'
    find('input#name').set 'TestRepo'
    find('input#name').set 'TestRepo'
    click_on 'Submit'
    # this causes an error which causes an modal to be dismissed
    wait_until(5) { first('.modal .modal-danger') }
    wait_until(5) { first('.modal .modal-danger').has_content? 'ERROR 403' }
    click_on 'Dismiss'
    wait_until(3) { all('.modal').empty? }
    expect(current_path).to match /projects\/new/
  end
end
