## Storage-Resources

All of the resources in this section are prefixed with `/cider-ci/storage`. This
prefix is omitted for brevity in the following. 


### General Properties 

#### Uploading 

Uploading is preformed via http `PUT`. A `PUT` request performed on an
existing path will not replace the existing resource but result with a
`409 Conflict` error .

Every storage artifact has an associated content-type. This content-type
is set from the content-type header of the `PUT` request and therefore
should be set properly. If not specified the content-type
`application/octet-stream` will be used.


#### Retention Time

Uploaded artifacts will be automatically deleted after a retention time
has been reached.

### Trial-Attachments

Trial-Attachments will be listed in the user-interface with the matching
trial. The default retention time is 10 days.

The `:trial_id` should be an existing id of a trial. This is however not
enforced. 

The `*` postfix can be an arbitrary url-path in accordance with the http
path specification, e.g. `screenshots/image01.png`. Query parameters will
be ignored. 

#### GET `/tree-attachments/:trial_id/*` 

#### PUT `/tree-attachments/:trial_id/*` 

#### DELETE `/tree-attachments/:trial_id/*` 


### Tree-Attachments

Tree-Attachments will be listed in the user-interface with the matching
execution. The default retention time is 90 days.

Similar restrictions as with the Trial-Attachments apply. 

#### GET `/tree-attachments/:tree_id/*` 

#### PUT `/tree-attachments/:tree_id/*` 

#### DELETE `/tree-attachments/:tree_id/*` 


