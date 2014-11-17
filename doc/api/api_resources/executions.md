
### Execution

#### GET `/execution/:id`

Returns the properties of the corresponding _Execution_.

### Execution-Stats

#### GET `/execution/:id/stats`

Returns the aggregated count of the number of tasks belonging to an _Execution_
with respect to their state. 


### Executions 

#### GET `/executions` 

Returns a list of links each pointing to an `Execution`. Order is
descending by the `created_at` time-stamp.

##### Query Parameters 

###### branch-name

Filters _Executions_ within branches with the name equal to the value of
the parameter.

Query string example: `?repository-name=Madek&branch-heads-only&branch-name=next` 

###### branch-heads-only

Filters only _Executions_ wich are on a current head of a branch.
Effective if and only if the key is present. 

Query string example: `?repository-name=Madek&branch-heads-only&branch-name=next` 

###### repository-name

Filters _Executions_ within repositories with the name equal to the value
of the parameter.

Query string example: `?repository-name=Madek&branch-heads-only&branch-name=next` 

###### state 

Filters _Executions_ with state equal to the value of the parameter.

Query string example: `?branch-name=master&repository-name=leihs&state=success`



