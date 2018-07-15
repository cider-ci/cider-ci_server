require 'active_support/all'
require 'pry'

require 'config/database'
require 'config/factories'
require 'config/web'
require 'helpers/global'

RSpec.configure do |config|

  config.include Helpers::Global

  config.before :each do
    srand 1
  end

end
