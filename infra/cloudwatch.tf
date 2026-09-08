# The backend container's own stdout/stderr, shipped here by the `awslogs` docker logging driver
# (templates/user_data.sh.tpl's production compose file only -- the repo-root docker-compose.yml used for local
# dev is untouched, since a local container has no AWS credentials/log group to ship to). Before this, the only
# copy of a production log line was `docker compose logs` on the instance itself -- gone the moment the container
# restarts (crash-loop, redeploy) past whatever the docker daemon's own log buffer still had.
resource "aws_cloudwatch_log_group" "backend" {
  name              = "/${var.project_name}/backend"
  retention_in_days = var.log_retention_days

  # Forces the terraform role's own logs:* policy (github_oidc_terraform.tf's ManageOwnCloudWatchLogGroup) to
  # apply *before* this log group is created -- same reasoning, and the same real failure mode, as
  # s3_backups.tf/s3_frontend.tf's identical depends_on: without it Terraform has no ordering constraint between
  # the two, so it's free to fire CreateLogGroup before the policy update granting it has actually taken effect.
  depends_on = [aws_iam_role_policy.github_actions_terraform]
}
