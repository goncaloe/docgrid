# Estado remoto e alerta de orçamento — aplicado uma única vez, antes de tudo o resto.
# Estado LOCAL (sem backend): este é o ovo-e-a-galinha que cria o bucket onde o estado
# da configuração principal vai viver. O estado local não é versionado — o .gitignore
# tem `*.tfstate`.
terraform {
  required_version = ">= 1.10"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
  }
}

provider "aws" {
  region = var.region

  default_tags {
    tags = {
      Project     = var.project_name
      ManagedBy   = "terraform"
      Environment = "demo"
    }
  }
}

# O nome do bucket tem de ser único a nível global; o id da conta resolve isso sem
# pedir um nome ao humano.
data "aws_caller_identity" "current" {}

# Bucket do estado remoto. Versionamento ligado: um tfstate corrompido/sobrescrito
# recupera-se. Encriptação SSE-S3 e bloqueio total de acesso público: o tfstate guarda
# segredos em claro (ver ADR 0018) — é por isto que este bucket é privado e encriptado.
resource "aws_s3_bucket" "state" {
  # #checkov:skip=CKV_AWS_18:Bucket de estado sem access logging — o acesso é por IAM da
  # conta e não há tráfego externo; logging num bucket de estado de demonstração é peso morto
  # #checkov:skip=CKV_AWS_144:Sem replicação entre regiões — ambiente de demonstração, estado
  # local num bucket, não um arquivo (ver ADR 0019)
  # #checkov:skip=CKV_AWS_145:SSE-S3 (AES256) de propósito, não KMS — a chave gerida pela AWS
  # para S3 é gratuita mas o desenho não depende de KMS nenhum (ver ADR 0019)
  # #checkov:skip=CKV2_AWS_61:Sem lifecycle no bucket de estado — versionamento ligado, nada
  # a expirar; o ciclo de vida não faz sentido num tfstate (ver ADR 0019)
  # #checkov:skip=CKV2_AWS_62:Sem notificações de eventos no bucket de estado — não há eventos
  # a processar num tfstate (ver ADR 0019)
  bucket = "${var.project_name}-tfstate-${data.aws_caller_identity.current.account_id}"
}

resource "aws_s3_bucket_versioning" "state" {
  bucket = aws_s3_bucket.state.id
  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "state" {
  bucket = aws_s3_bucket.state.id
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_public_access_block" "state" {
  bucket                  = aws_s3_bucket.state.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# Alerta de orçamento ANTES de existir qualquer recurso pago — o requisito do briefing.
# O limite é em moeda de faturação da conta (5 EUR para contas europeias); o Budgets
# não tem campo de moeda.
resource "aws_sns_topic" "budget_alerts" {
  name = "${var.project_name}-budget-alerts"
  # SSE com a chave gerida pela AWS (alias/aws/sns) — gratuita e elimina o CKV_AWS_26.
  kms_master_key_id = "alias/aws/sns"
}

resource "aws_sns_topic_subscription" "budget_alerts_email" {
  topic_arn = aws_sns_topic.budget_alerts.arn
  protocol  = "email"
  endpoint  = var.alert_email
}

resource "aws_budgets_budget" "monthly" {
  name         = "${var.project_name}-monthly-budget"
  budget_type  = "COST"
  limit_amount = "5"
  limit_unit   = "USD"
  time_unit    = "MONTHLY"

  # 50%, 80% e 100%, no valor real e no previsto — 6 notificações, todas para o mesmo
  # tópico. A 100% do previsto o alarme dispara antes de a fatura, não depois.
  dynamic "notification" {
    for_each = [50, 80, 100]
    content {
      comparison_operator       = "GREATER_THAN"
      threshold                 = notification.value
      threshold_type            = "PERCENTAGE"
      notification_type         = "ACTUAL"
      subscriber_sns_topic_arns = [aws_sns_topic.budget_alerts.arn]
    }
  }
  dynamic "notification" {
    for_each = [50, 80, 100]
    content {
      comparison_operator       = "GREATER_THAN"
      threshold                 = notification.value
      threshold_type            = "PERCENTAGE"
      notification_type         = "FORECASTED"
      subscriber_sns_topic_arns = [aws_sns_topic.budget_alerts.arn]
    }
  }
}