## Core API Resources

All of the resources in this section are prefixed with `/cider-ci/api/v2`. This
prefix is omitted for brevity in the following. 


### General Properties 

#### Content Types 

The core API supports the content types `application/json-roa+json` and
`application/json` for `GET` requests to all resources. The former
includes hyperlinks to related resources and to this documentation.

The API evaluates the accept header including the `q` parameter, see [Section
14 of RFC 2616][] . The content type `application/json-roa+json` as a higher
quality of service and is thus preferred. If no matching accept header is
supplied the content-type `application/json-roa+json` will be used.

All data requests containing a body like `PUT`, `PATCH`, and `POST`
support the content type `application/json`.


  [Section 14 of RFC 2616]: http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html


#### Index Resources

Several resources are conceptually indexes, e.g. `/executions/`, which rather
return several references to the same kind of resource, e.g. `/execution/:id`.
All of these indexes are paginated an use the `page` parameter with an integer
value for iteration. 

The content type `application/json-roa+json` includes a `next` link which 
should be used to iterate index resources.


