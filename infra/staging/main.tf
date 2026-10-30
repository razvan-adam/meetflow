# Staging environment (MF-37). Not created yet: see docs/deployment.md before running plan or apply.
#
# The state bucket name contains the AWS account ID, so it is passed at init time:
#   terraform init -backend-config="bucket=meetflow-tfstate-<account id>"

terraform {
  required_version = ">= 1.16"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.71"
    }
  }

  backend "s3" {
    key          = "staging/terraform.tfstate"
    region       = "eu-west-1"
    use_lockfile = true
    encrypt      = true
  }
}

provider "aws" {
  region = "eu-west-1"

  default_tags {
    tags = {
      Project     = "meetflow"
      Environment = "staging"
      ManagedBy   = "terraform"
    }
  }
}

module "environment" {
  source = "../modules/environment"

  name              = "staging"
  vpc_cidr          = "10.20.0.0/16"
  github_repository = "razvan-adam/meetflow"
}

output "url" {
  value = module.environment.url
}

output "github_variables" {
  description = "Values for the staging environment's variables in GitHub (docs/deployment.md)."
  value = {
    AWS_ROLE_ARN               = module.environment.deploy_role_arn
    AWS_REGION                 = module.environment.aws_region
    ECR_REPOSITORY             = module.environment.ecr_repository
    ECS_CLUSTER                = module.environment.ecs_cluster
    ECS_SERVICE                = module.environment.ecs_service
    TASK_FAMILY                = module.environment.task_family
    FRONTEND_BUCKET            = module.environment.frontend_bucket
    CLOUDFRONT_DISTRIBUTION_ID = module.environment.cloudfront_distribution_id
    STAGING_URL                = module.environment.url
  }
}
