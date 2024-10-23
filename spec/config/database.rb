require 'sequel'

def database_name
  ENV['CIDER_CI_DATABASE_NAME'].presence ||
    ENV['PGDATABASE'].presence ||
    'cider-ci'
end

def database_user
  ENV['CIDER_CI_DATABASE_USER'].presence ||
    ENV['PGUSER'].presence ||
    ENV['USER'].presence ||
    'postgres'
end

def database_password
  ENV['CIDER_CI_DATABASE_PASSWORD'].presence ||
    ENV['PGPASSWORD'].presence
end

def database_host
  ENV['CIDER_CI_DATABASE_HOST'].presence ||
    ENV['PGHOST'].presence ||
    'localhost'
end

def database_port
  Integer(
    ENV['CIDER_CI_DATABASE_PORT'].presence ||
    ENV['PGPORT'].presence ||
    5415)
end

def database
  @database ||= Sequel.postgres(
    database: database_name,
    user: database_user,
    password: database_password,
    host: database_host ,
    port: database_port)
end

def clean_db
  tables = database[ <<-SQL.strip_heredoc
    SELECT table_name
      FROM information_schema.tables
    WHERE table_type = 'BASE TABLE'
    AND table_schema = 'public'
    ORDER BY table_type, table_name;
    SQL
  ].map{|r| r[:table_name]}.reject { |tn| tn == 'schema_migrations' } \
    .join(', ').tap do |tables|
    database.run" TRUNCATE TABLE #{tables} CASCADE; "
  end
end

RSpec.configure do |config|
  config.before(:each) do
    clean_db
  end
end
