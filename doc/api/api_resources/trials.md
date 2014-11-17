### Trial

#### GET `/trial/:id` 

Returns the properties of the trial with the id `:id`.


### Trials

#### GET `/task/:task_id/trials` 

Returns a list of links each pointing to a trial which belongs to the
task with the id `:task_id`. Order is descending with respect to the
`updated_at` property.

##### Query Parameters 

###### state 

Filters trials with state equal to the value of the parameter.

Query string example: `?page=0&state=failed`


