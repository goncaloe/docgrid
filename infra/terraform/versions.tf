# Configuração principal do DocGrid em AWS. O estado vive no bucket criado pelo
# `bootstrap/` — aplica-se o bootstrap primeiro, sempre (ver infra/terraform/README.md).
terraform {
  required_version = ">= 1.10"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.6"
    }
  }

  backend "s3" {
    # Substituir pelo bucket que o bootstrap criou (docgrid-tfstate-<account_id>,
    # ver output do bootstrap). O bloco backend não aceita variáveis — é literal.
    bucket = "docgrid-tfstate-123456789012"
    key    = "docgrid/terraform.tfstate"
    region = "eu-west-1"
    # Bloqueio nativo do S3 desde o Terraform 1.10 — dispensa a tabela DynamoDB (ADR 0017).
    use_lockfile = true
  }
}