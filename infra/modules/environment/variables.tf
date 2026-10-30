variable "name" {
  description = "Environment name, used in resource names (e.g. staging)."
  type        = string
}

variable "vpc_cidr" {
  description = "Address range of the environment's VPC (a /16)."
  type        = string
}

variable "github_repository" {
  description = "Repository (owner/name) whose deploy workflow may deploy this environment."
  type        = string
}

variable "backend_image_tag" {
  description = "Image tag in Terraform's task definition. Deploys register newer revisions themselves, so this only matters until the first deploy."
  type        = string
  default     = "initial"
}

variable "backend_cpu" {
  description = "Fargate CPU units for the backend task (1024 = 1 vCPU)."
  type        = number
  default     = 512
}

variable "backend_memory" {
  description = "Fargate memory for the backend task, in MiB."
  type        = number
  default     = 1024
}

variable "backend_desired_count" {
  description = "Number of backend tasks."
  type        = number
  default     = 1
}

variable "db_instance_class" {
  description = "RDS instance class."
  type        = string
  default     = "db.t4g.micro"
}

variable "db_allocated_storage" {
  description = "RDS storage, in GiB."
  type        = number
  default     = 20
}

variable "log_retention_days" {
  description = "How long backend logs are kept."
  type        = number
  default     = 30
}
