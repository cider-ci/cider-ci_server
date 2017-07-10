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
