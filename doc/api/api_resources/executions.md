
### Execution

#### GET `/execution/:id`

Returns the properties of the corresponding _Execution_.

### Execution-Stats

#### GET `/execution/:id/stats`

Returns the aggregated count of the number of tasks belonging to an _Execution_
with respect to their state. 


### Executions 

#### GET `/executions/` 

Returns a list of links each pointing to an `Execution`. Order is
descending by the `created_at` time-stamp.

##### Query Parameters 

###### branch

Filters *Executions* of commits which are directly referenced by the given
branch with name equal to the given value where the matching is case
insensitive.

Query string example: `?branch=master` 

Note: this filter is case insensitive with respect to _branch-name_.

###### branchdescendants

Similar as above, however it will also include all descendants
of the commit referenced by the given branch name.

Examples: 

1.  Descendants of the branch master: `?branch=master`

2.  A combination of `branch` and `branchdescendants` can make sense:
    `?branchdescendants=next&branch=master`, read: give me the
    executions of the commit where the branch master points to and which
    are descendants of the branch next.

###### repository

Filters *Executions* related to commits within repositories with the
name equal to the value of the parameter.

This filter is also case insensitive. 

Examples: 

1. executions for the _Madek_ repository: `?repository=madek` 
2. for the _Madek_ repository and for the master branch: `?repository=madek&branch=master`
3. including descendants: `?repository=madek&branchdescendants=master`
4. composing even more: `?repository=madek&branchdescendants=next&branch=master`


###### state 

Filters _Executions_ with state equal to the value of the parameter.

Query string example: `?branch-name=master&repository-name=leihs&state=success`



