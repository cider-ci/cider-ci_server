require 'spec_helper'
require 'pry'

feature 'Basic', type: :feature do
  scenario 'Front page has some content', type: :feature do
    visit '/cider-ci/'
    expect(page).to have_content 'Cider-CI'
  end
end
