
### Task

#### GET `/task/:id` 

Returns the properties of the task.


### Tasks

#### GET `/execution/:execution_id/tasks` 

Returns a list of links each pointing to a task which belongs to
execution with the id `:execution_id`. Order is ascending with respect
to the `name` property.

##### Query Parameters 

###### state 

Filters tasks with state equal to the value of the parameter.

Query string example: `?page=0&state=failed`



