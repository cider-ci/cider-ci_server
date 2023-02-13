export PGPORT=${PGPORT:-5432}
export PGUSER=${PGUSER:-${USER}}
export PGPASSWORD=${PGPASSWORD:-${PGUSER}}
export DATABASE_NAME=${DATABASE_NAME:-'cider-ci_v5'}
export PGDATABASE=${DATABASE_NAME}
export DBURL="jdbc:postgresql://${PGUSER}:${PGPASSWORD}@localhost:${PGPORT}/${PGDATABASE}"


function terminate_connections {
psql -d template1  <<SQL
  SELECT pg_terminate_backend(pg_stat_activity.pid) 
    FROM pg_stat_activity 
    WHERE pg_stat_activity.datname = '$PGDATABASE' 
      AND pid <> pg_backend_pid();
SQL
}

