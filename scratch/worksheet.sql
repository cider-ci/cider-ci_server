
SELECT trials.*
FROM trials
INNER JOIN tasks ON trials.task_id = tasks.id
INNER JOIN jobs ON tasks.job_id = jobs.id
INNER JOIN commits ON jobs.tree_id = commits.tree_id
INNER JOIN branches_commits ON commits.id = branches_commits.commit_id
INNER JOIN branches ON branches_commits.branch_id = branches.id
INNER JOIN repositories ON branches.repository_id = repositories.id
WHERE ((trials.state = 'pending'
        AND (exists(
                      (SELECT 1
                       FROM executors_with_load
                       WHERE ((((relative_load < 1
                                 AND enabled = TRUE)
                                AND (tasks.traits <@ executors_with_load.traits))
                               AND (last_ping_at > (now() - interval '1 Minutes')))
                              AND (executors_with_load.accepted_repositories = '{}'))))
             OR exists(
                         (SELECT 1
                          FROM executors_with_load
                          INNER JOIN tasks ON trials.task_id = tasks.id
                          INNER JOIN jobs ON tasks.job_id = jobs.id
                          INNER JOIN commits ON jobs.tree_id = commits.tree_id
                          INNER JOIN branches_commits ON commits.id = branches_commits.commit_id
                          INNER JOIN branches ON branches_commits.branch_id = branches.id
                          INNER JOIN repositories ON branches.repository_id = repositories.id
                          WHERE repositories.git_url = ANY(executors_with_load.accepted_repositories)))))
       AND NOT EXISTS(
                        (SELECT 1
                         FROM trials active_trials
                         INNER JOIN tasks active_tasks ON active_tasks.id = active_trials.task_id
                         WHERE ((active_trials.state IN ('executing', 'dispatching'))
                                AND active_tasks.exclusive_global_resources && tasks.exclusive_global_resources))))
ORDER BY jobs.priority DESC,
         jobs.created_at ASC,
         tasks.priority DESC,
         tasks.created_at ASC,
         trials.created_at ASC LIMIT 1
;


SELECT executors_with_load.accepted_repositories, trials.id, repositories.git_url
FROM executors_with_load, trials
INNER JOIN tasks ON trials.task_id = tasks.id
INNER JOIN jobs ON tasks.job_id = jobs.id
INNER JOIN commits ON jobs.tree_id = commits.tree_id
INNER JOIN branches_commits ON commits.id = branches_commits.commit_id
INNER JOIN branches ON branches_commits.branch_id = branches.id
INNER JOIN repositories ON branches.repository_id = repositories.id
WHERE TRUE
  AND repositories.git_url = ANY(executors_with_load.accepted_repositories)
  AND trials.state = 'pending'
  ;


