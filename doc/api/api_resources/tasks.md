
### Task

#### GET `/task/:id` 

Returns the properties of the corresponding `Task`.


### Tasks

#### GET `/execution/:execution_id/tasks/` 

Returns a list of links each pointing to a `Task` which belongs to
`Execution` with the id `:execution_id`. Order is ascending with respect
to the `name` property.

##### Query Parameters 

###### state 

Filters `Tasks` with state equal to the value of the parameter.

Query string example: `?page=0&state=failed`



