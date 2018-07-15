require 'sequel'

module Helpers
  module DB
    extend self

    def database
      db_user = ENV['PGUSER'].presence
      db_password = ENV['PGPASSWORD'].presence
      db_name = ENV['DATABASE_NAME'].presence
      Sequel.connect(
        if db_user && db_password && db_name
          "postgresql://#{db_user}:#{db_password}" \
          "@localhost:#{ENV['PGPORT']}/#{db_name}?pool=3"
        else
          'postgresql://cider-ci:secret@localhost/cider-ci_v4?pool=3'
        end)
    end
  end
end
