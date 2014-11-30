% Cider-CI API Documentation 
% Thomas Schank <DrTom@Schank.ch>
% 2014-11-18


Cider-CI API Documentation 
==========================

Visit  the [Cider-CI Project](https://github.com/cider-ci/cider-ci) for more information on Cider-CI.

The [entities diagram](https://rawgit.com/cider-ci/cider-ci/master/doc/entities.svg) is helpful to understand relations between resources. 


## State of the API 

**The Cider-CI API is beta.**

This Cider-CI API and in particular the responses of type
`application/json-roa+json` is work in progress. Breaking changes in the of
the [JSON-ROA] part of the API do not necessarily increase the major number of
the API and certainly not those of Cider-CI itself.  

The current major release number of the API itself is `2`. This is reflected in
the prefix `/api/v2` prefix. The major number was lifted during the migration
from [HAL][] to [JSON-ROA][].

  [HAL]: http://stateless.co/hal_specification.html
  [JSON-ROA]: https://github.com/json-roa

## Authentication 

### Session-Cookie 

The API accepts the `cider-ci_services-session` cookie set on sing-in
from the browser interfaces. 

### Basic-Auth

If no session cookie was found (or validation of the cookie failed)
*HTTP Basic authentication* is used with the same user and password as
used in the browser interface.



