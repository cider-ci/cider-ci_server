### Trial-Attachment

#### GET `/trial-attachment/:id`

Returns the properties of a  _Trial-Attachment_.

This does not return the data-stream of the attachment itself. A
hyperlink to the latter is included in `application/json-roa+json`
response types, see [Content Types][].

### Trial-Attachments

#### GET `/trial/:trial_id/trial-attachments` 

Returns a list of ids. Each belongs to a _Trial-Attachment_ which belongs to the
trial with the id `:trial_id`. Order is ascending with respect to the `path`
property.

