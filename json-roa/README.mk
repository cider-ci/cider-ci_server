

Content-type negotiation
------------------------

This middleware uses `ring-middleware-accept` to negotiate content types. It
acts **if and only if** "application/roa+json" or "application/roa" is   an
accepted content type by the corresponding header, where
"application/roa+json" is slighlty favord by an corresponding quality of
service setting. It passes on the request to the default handler without
performing any action otherwise with the minor sideffect of setting `{:accept
{:mime ...}}` in the request hash. 

Content type negotiation will not take place if `{:accept {:mime ...}}` is
	already set in the request hash. This can be used to force a JSON-ROA to be
	build, even if no or no appropiate accept header is given, 
	"application/json" e.g.,  by setting `:accept {:mime "application/roa+json"}` in
	the request before this wrapper is invoked during the request/response cycle. 
