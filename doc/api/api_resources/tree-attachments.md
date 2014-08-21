### Tree-Attachment

#### GET `/tree-attachment*` 

Returns the properties of a tree-attachment which path equals the
postfix denoted by the `*` in the route above.

This does not return the data-stream of the attachment itself. A link to
the latter is included in the `_links` property.


### Tree-Attachments

#### GET `/execution/:execution_id/attachments` 

Returns a list of links each pointing to a Tree-Attachment which belongs
to the execution with the id `:execution_id`. Order is ascending with
respect to the `path` property.

