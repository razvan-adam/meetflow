terraform {
  required_version = ">= 1.16"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.71"
    }
  }
}

data "aws_caller_identity" "current" {}

data "aws_region" "current" {}

locals {
  prefix     = "meetflow-${var.name}"
  account_id = data.aws_caller_identity.current.account_id
  region     = data.aws_region.current.region
}
