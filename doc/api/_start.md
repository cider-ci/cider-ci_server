% Cider-CI API Documentation 
% Thomas Schank <DrTom@Schank.ch>
% 2014-10-07


Cider-CI API Documentation 
==========================

Visit  the [Cider-CI Project](https://github.com/cider-ci/cider-ci) for more information on Cider-CI.

The [entities diagram](https://rawgit.com/cider-ci/cider-ci/master/doc/entities.svg) is helpful to understand relations between resources. 

## Authentication 

### Session-Cookie 

The API accepts the `cider-ci_services-session` cookie set on sing-in
from the browser interfaces. 

### Basic-Auth

If no session cookie was found (or validation of the cookie failed)
*HTTP Basic authentication* is used with the same user and password as
used in the browser interface.

