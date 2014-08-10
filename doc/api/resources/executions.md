
### Execution

#### GET `/executions/:id`

Returns the properties of the execution.

### Execution-Stats

#### GET `/executions/:id/stats`

Returns the aggregated count of the number of tasks belonging to an execution
with respect to their state. 


### Executions 

#### GET `/executions` 

Returns a list of links each pointing to an execution. Order is
descending by the `created_at` time-stamp.

##### Query Parameters 

###### branch-name

Filters executions within branches with the name equal to the value of
the parameter.

Query string example: `?repository-name=Madek&branch-heads-only&branch-name=next` 

###### branch-heads-only

Filters only executions wich are on a current head of a branch.
Effective if and only if the key is present. 

Query string example: `?repository-name=Madek&branch-heads-only&branch-name=next` 

###### page 

Used for pagination. Do not set this value. Use the `next` and
`previous` links to iterate over paginated results.

###### repository-name

Filters executions within repositories with the name equal to the value
of the parameter.

Query string example: `?repository-name=Madek&branch-heads-only&branch-name=next` 

###### state 

Filters executions with state equal to the value of the parameter.

Query string example: `?branch-name=master&repository-name=leihs&state=success`



