## Core API Resources

All of the resources in this section are prefixed with `/cider-ci/api/v2`. This
prefix is omitted for brevity in the following. 


### General Properties 

#### Content Types 

The core API supports the content types `application/json-roa+json` and
`application/json` for `GET` requests to all resources. The former
includes hyperlinks to related resources and to this api documentation.

The API evaluates the accept header including the `q` parameter, see [Section
14 of RFC 2616][] . The content type `application/json-roa+json` as a higher
quality of service and is thus proffered. If no matching accept header is
supplied the content-type `application/json-roa+json` will be used.


All data requests containing a body like `PUT`, `PATCH`, and `POST`
support the content type `application/json`



  [Section 14 of RFC 2616]: http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html


