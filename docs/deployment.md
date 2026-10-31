# MeetFlow – Deployment

How the staging environment is built, deployed and rolled back (MF-37). Decisions and alternatives are in `docs/architecture.md`.

> **Status: built, not created.** The Terraform code, the backend image and the deploy workflow are written and checked in CI, but nothing exists in AWS yet, so there are no AWS costs. Deploys are off until the [first-time setup](#first-time-setup) is done. Don't run `terraform plan` or `apply` against an AWS account until the team decides to create the environment.

## Staging at a glance

```
Browser ── HTTPS ──► CloudFront (*.cloudfront.net)
                      ├── /*      ► S3 bucket (React app, private, read through origin access control)
                      └── /api/*  ► VPC origin ► internal ALB (private subnets)
                                                   └── ECS Fargate task, ARM64 (public subnet, inbound from the ALB only)
                                                         ├── RDS PostgreSQL 18 (private subnet)
                                                         └── Secrets Manager (database credentials, managed by RDS)
```

- **Region:** `eu-west-1` (Ireland). All data, backups and logs stay there.
- **Code:** `infra/bootstrap` (one-time, account-wide: state bucket and GitHub's OIDC provider), `infra/modules/environment` (everything above, reusable for production), `infra/staging` (the staging values).
- **Backend:** one task, 0.5 vCPU and 1 GiB. Flyway migrates the database when the backend starts, so every deploy runs pending migrations before the new version takes traffic.
- **HTTPS:** CloudFront is the only public endpoint and redirects HTTP to HTTPS (the API answers HTTPS only). The backend reads the browser's protocol from `CloudFront-Forwarded-Proto` (`application.properties`), so the `XSRF-TOKEN` cookie is Secure and HSTS is sent.
- **Logs:** CloudWatch log group `/ecs/meetflow-staging-backend`, kept 30 days. Meeting content is never logged.
- **Estimated cost:** about 50–60 USD/month (ALB ~18, RDS `db.t4g.micro` with 20 GiB ~16, Fargate ~14, public IPv4 ~4, CloudFront, S3, logs and secrets a few dollars).

## First-time setup

Done once, by someone with administrator access to the AWS account. Needs Terraform 1.16 or later and the AWS CLI.

1. **Sign in to AWS** on your machine, e.g. `aws configure sso`, and check with `aws sts get-caller-identity`.
2. **Bootstrap the account** (state bucket and GitHub OIDC provider):
   ```sh
   cd infra/bootstrap
   terraform init
   terraform apply
   ```
   Note the `state_bucket` output (`meetflow-tfstate-<account id>`). This stack's state stays in a local, git-ignored file; if it is lost, the two resources can be imported again. If the account already has a GitHub OIDC provider, import it first: `terraform import aws_iam_openid_connect_provider.github arn:aws:iam::<account id>:oidc-provider/token.actions.githubusercontent.com`.
3. **Create staging:**
   ```sh
   cd infra/staging
   terraform init -backend-config="bucket=meetflow-tfstate-<account id>"
   terraform plan -out staging.tfplan
   terraform apply staging.tfplan
   ```
   Creating RDS and CloudFront takes 10–20 minutes. Until the first deploy, the ECS service shows a failed deployment: its first task definition points to an image tag (`initial`) that doesn't exist yet. That's expected.
4. **Create the GitHub environment** `staging` (repository Settings → Environments), and under *Deployment branches* allow `main` only. Add the values from `terraform output github_variables` as environment variables (not secrets; none of them is secret):
   `AWS_ROLE_ARN`, `AWS_REGION`, `ECR_REPOSITORY`, `ECS_CLUSTER`, `ECS_SERVICE`, `TASK_FAMILY`, `FRONTEND_BUCKET`, `CLOUDFRONT_DISTRIBUTION_ID`, `STAGING_URL`.
5. **Turn deploys on:** add the repository variable `STAGING_DEPLOY_ENABLED` = `true` (Settings → Secrets and variables → Actions → Variables).
6. **First deploy:** Actions → *Deploy to staging* → *Run workflow*, with the latest commit on `main` (`git rev-parse origin/main`). When it finishes, open `STAGING_URL`: the login page should load, and signing up should land on the dashboard.

## Deploying

Automatic: every push to `main` (every merged pull request) runs CI, and when the frontend, backend and infrastructure jobs pass, the *Deploy to staging* job runs `.github/workflows/deploy.yml`:

1. Builds the backend jar and image for that commit (tag = commit SHA) and pushes it to ECR. If the image already exists, it is reused.
2. Registers a new task definition revision (the latest revision with the new image) and updates the ECS service. New tasks start, Flyway migrates, and the ALB switches traffic once `/api/health` answers 200; then the old tasks stop.
3. Waits for the service to be stable and checks that it runs the new revision. If the new tasks never get healthy, ECS rolls back to the previous revision on its own (deployment circuit breaker) and the job fails.
4. Builds the frontend and uploads it to S3 (hashed assets cached for a year, `index.html` always revalidated), then invalidates `index.html` in CloudFront.
5. Smoke test: `GET /api/health` on the staging URL answers `UP` with a Secure `XSRF-TOKEN` cookie and an HSTS header, and the app's page loads.

Only one deploy runs at a time; a newer one waits for the running one.

## Rolling back

Redeploy the last good commit:

- Actions → *Deploy to staging* → *Run workflow*, with that commit's SHA, or
- `gh workflow run deploy.yml -f ref=<sha>`

The image for that commit is still in ECR (the last 30 are kept), so it is reused rather than rebuilt; the frontend is rebuilt from that commit.

**Migrations are not rolled back.** Flyway only moves forward, so the older backend runs against the newer schema. Write migrations so the previous release still works with them (add columns and tables first, remove old ones in a later release), and fix a bad migration with a new migration, never by editing an applied one. A merge to `main` after a rollback deploys again as usual; revert the bad commit first if it shouldn't go out.

## Changing the infrastructure

1. Change the Terraform code in a pull request. CI checks formatting (`terraform fmt`) and validates both stacks without AWS access. Format locally with `terraform -chdir=infra fmt -recursive`.
2. After merging, someone with AWS access runs `terraform plan` and `terraform apply` in `infra/staging`. There is no automatic apply yet.
3. Changes to the task definition (environment variables, CPU, memory) reach the running service with the next deploy, which copies the latest revision.

## Removing staging

The database has deletion protection and takes a final snapshot. To delete everything: set `deletion_protection = false` in `infra/modules/environment/database.tf`, apply, then `terraform destroy` in `infra/staging`. Delete the final snapshot in RDS afterwards if it isn't needed, and set `STAGING_DEPLOY_ENABLED` to `false`.
