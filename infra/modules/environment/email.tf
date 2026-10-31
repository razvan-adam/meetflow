# Amazon SES: the app's emails (invitations now; minutes and task emails later).
#
# A new SES account is in the sandbox: it only sends to verified addresses, at most 200 a day.
# For testing staging, verify the testers' addresses too, or request production access
# (docs/deployment.md).

resource "aws_sesv2_email_identity" "sender" {
  email_identity = var.email_from
}

# The backend's own permissions while running (the execution role only starts the task).
resource "aws_iam_role" "backend_task" {
  name               = "${local.prefix}-backend-task"
  assume_role_policy = data.aws_iam_policy_document.ecs_tasks_assume.json
}

resource "aws_iam_role_policy" "backend_task_send_email" {
  name = "send-email"
  role = aws_iam_role.backend_task.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = "ses:SendEmail"
      Resource = aws_sesv2_email_identity.sender.arn
      Condition = {
        StringEquals = { "ses:FromAddress" = var.email_from }
      }
    }]
  })
}
