# Cider-CI Server
=======

Part of [Cider-CI](https://github.com/cider-ci/cider-ci).

## License

Copyright (C) 2013 - 2017 Dr. Thomas Schank  (DrTom@schank.ch, Thomas.Schank@algocon.ch)
Licensed under the terms of the GNU Affero General Public License v3.
See the "LICENSE.txt" file provided with this software.


## Usage

### Database Migrations

    lein run -- server migrate -d "jdbc:postgresql://cider-ci:cider-ci@localhost:5415/cider-ci"

### Database Setup

    CREATE ROLE "cider-ci" superuser LOGIN PASSWORD 'cider-ci'

    CREATE DATABASE "cider-ci" OWNER "cider-ci";

