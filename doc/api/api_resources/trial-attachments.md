### Trial-Attachment

#### GET `/trial-attachment*` 

Returns the properties of a trial-attachment which path equals the
postfix denoted by the `*` in the route above.

This does not return the data-stream of the attachment itself (no matter
what the path extension might suggest). A link to the latter is included
in the `_links` property.


### Trial-Attachments

#### GET `/trial/:trial_id/trial-attachments` 

Returns a list of links each pointing to a trial-attachment which
belongs to the trial with the id `:trial_id`. Order is ascending with
respect to the `path` property.

