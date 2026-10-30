# The deploy workflow reads these as GitHub environment variables (docs/deployment.md).

output "url" {
  description = "Public HTTPS address of the environment (STAGING_URL)."
  value       = "https://${aws_cloudfront_distribution.main.domain_name}"
}

output "deploy_role_arn" {
  description = "Role the deploy workflow assumes (AWS_ROLE_ARN)."
  value       = aws_iam_role.deploy.arn
}

output "aws_region" {
  description = "AWS_REGION."
  value       = local.region
}

output "ecr_repository" {
  description = "ECR_REPOSITORY."
  value       = aws_ecr_repository.backend.name
}

output "ecs_cluster" {
  description = "ECS_CLUSTER."
  value       = aws_ecs_cluster.main.name
}

output "ecs_service" {
  description = "ECS_SERVICE."
  value       = aws_ecs_service.backend.name
}

output "task_family" {
  description = "TASK_FAMILY."
  value       = aws_ecs_task_definition.backend.family
}

output "frontend_bucket" {
  description = "FRONTEND_BUCKET."
  value       = aws_s3_bucket.frontend.bucket
}

output "cloudfront_distribution_id" {
  description = "CLOUDFRONT_DISTRIBUTION_ID."
  value       = aws_cloudfront_distribution.main.id
}
