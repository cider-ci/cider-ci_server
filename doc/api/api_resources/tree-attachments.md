### Tree-Attachment

#### GET `/tree-attachment/:id` 

Returns the properties of a _Tree-Attachment_.

This does not return the data-stream of the attachment itself. A
hyperlink to the latter is included in `application/json-roa+json`
response types, see [Content Types][].


### Tree-Attachments

#### GET `/execution/:execution_id/attachments/` 

Returns a list of ids each belonging to a _Tree-Attachment_ which belongs
to the _Execution_ with the id `:execution_id`. Note, the `tree_id` is a
inherent property of every _Execution_. Order is ascending with respect to
the `path` property.

