# DocGrid infrastructure on AWS

> **Warning first:** this infrastructure has **never been applied**. It is written,
> validated and reviewed, but there is no AWS account behind it: it is a design artifact,
> one `apply` away from being real. ADR 0019 explains what this validates and what it
> does **not** validate. There is no public URL, no deploy, nothing being billed.

## What it would raise

A complete demo environment from a single `terraform apply`:

- VPC `10.0.0.0/16` with no NAT: one public subnet (the instance) and two private ones
  (RDS), a free S3 gateway endpoint on both route tables, minimal security groups
  (app: 80 from CloudFront only; db: 5432 from the app only).
- RDS Postgres 16 (`db.t4g.micro`, 20 GB, encrypted, no backups - demo), S3 (documents
  and site, both private and encrypted), main SQS queue + DLQ mirroring the local design
  (`VisibilityTimeout=120`, `maxReceiveCount=3`).
- A `t4g.micro` instance (ARM64, AL2023) running docker compose (app + Caddy); the
  user-data reads the SSM parameters and a systemd unit survives reboots.
- CloudFront with two origins: S3 for the SPA (OAC), the instance for `/api/*` with
  `X-Origin-Verify`; CORS on the documents bucket for direct browser uploads.
- CloudWatch: `/docgrid/app` log group (7 days), alarms for a full DLQ and a dead
  instance, email notifications.
- `bootstrap/` separately: the remote state bucket (versioned, SSE, private) and the
  5 EUR budget alert **before** any paid resource.

Estimated monthly cost in the pricing section of `docs/COSTS.md` (~26 EUR).

## Order

`bootstrap` first, always - the main configuration points its remote backend at the
bucket bootstrap creates; without it, `terraform init` of the main configuration fails.

```bash
# 1. Bootstrap (local state, once)
cd infra/terraform/bootstrap
terraform init
terraform apply          # asks for the alert email (terraform.tfvars or -var)

# 2. Main configuration (remote state)
cd infra/terraform
cp terraform.tfvars.example terraform.tfvars   # fill in image_reference and alert_email
terraform init            # uses the s3 backend of versions.tf (replace the literal bucket)
terraform plan
terraform apply
```

### While there is no AWS account - what can be verified offline

```bash
terraform -chdir=infra/terraform init -backend=false
terraform -chdir=infra/terraform validate
terraform -chdir=infra/terraform fmt -check -recursive
terraform -chdir=infra/terraform/bootstrap init -backend=false
terraform -chdir=infra/terraform/bootstrap validate
tflint --chdir=infra/terraform
checkov -d infra/terraform
shellcheck infra/terraform/templates/user-data.sh.tftpl
```

`terraform plan` still fails, and that is expected: the AWS provider calls
`sts:GetCallerIdentity` while setting up, and the CloudFront prefix
(`aws_ec2_managed_prefix_list`) contacts AWS during the plan only. Without credentials,
`validate` is the ceiling. Never invent credentials or `mock_provider` to force a plan.

## Known acceptances of this configuration (ADR 0019)

- No NAT: the saving the brief assumed with VPC endpoints inverts at this scale; only
  the free S3 gateway endpoint comes in.
- No WAF, no flow logs, no RDS backups, no versioning on the content buckets, no
  cross-region replication: all accepted by design, with the reason on each
  `checkov:skip` and consolidated in ADR 0019.
- The managed SSM Session Manager policy (`AmazonSSMManagedInstanceCore`) fails the
  Terraform provider ARN validation; it attaches with one CLI command during `apply`:

  ```bash
  aws iam attach-role-policy --role-name docgrid-instance \
    --policy-arn arn:aws:iam::policy/AmazonSSMManagedInstanceCore
  ```

- The literal bucket in the `backend "s3"` block of `versions.tf` is replaced by the one
  bootstrap creates (`docgrid-tfstate-<account_id>`).

## Tearing it all down

```bash
cd infra/terraform && terraform destroy   # the main configuration
cd infra/terraform/bootstrap && terraform destroy   # state bucket and alert, last
```

A `terraform destroy` of the main configuration removes everything that bills; orphaned
resources would be a failure of this stage (see its brief).

Pointers: prices and how to shut everything down in `docs/COSTS.md`; why this is written
and not applied, in ADR `docs/adr/0019-infraestrutura-como-deseno.md`.