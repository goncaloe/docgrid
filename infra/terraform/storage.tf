# Dois buckets: documentos e site. Ambos privados e encriptados (SSE-S3, chave gerida
# pela AWS). O de documentos tem ciclo de vida — é um ambiente de demonstração, não um
# arquivo. Os nomes de buckets são únicos a nível global: se um estiver ocupado, ajusta
# o nome (e, na poliza da fila, o condicion aws:SourceArn — ver queue.tf).

resource "aws_s3_bucket" "documents" {
  # #checkov:skip=CKV_AWS_18:Bucket de documentos sem access logging — escala de
  # demonstração; o upload vai a CloudFront e o rastro de gestão está noutra parte (ADR 0019)
  # #checkov:skip=CKV_AWS_21:Versioning desligado — um PUT repõe o objeto e versões antigas
  # de faturas de demonstração não interessam a ninguém (ver ADR 0019)
  # #checkov:skip=CKV_AWS_144:Sem replicação entre regiões — ambiente de demonstração,
  # documentos efémeros com ciclo de vida de 90 dias (ver ADR 0019)
  # #checkov:skip=CKV_AWS_145:SSE-S3 (AES256) de propósito, não KMS — a chave gerida da
  # AWS para S3 é gratuita mas o desenho não depende de KMS nenhum (ver ADR 0019)
  # #checkov:skip=CKV2_AWS_62:Notificação S3→SQS definida com aws_s3_bucket_notification
  # em queue.tf — estes skips não escondem nada, é o checkov que não liga os ficheiros (ADR 0019)
  bucket = "${var.project_name}-documents"
}

resource "aws_s3_bucket_lifecycle_configuration" "documents" {
  bucket = aws_s3_bucket.documents.id

  rule {
    id     = "expire-demo-documents"
    status = "Enabled"
    expiration {
      days = 90
    }
    # Um upload do browser ao S3 pode abortar a meio; sem isto, as partes acumulam-se.
    abort_incomplete_multipart_upload {
      days_after_initiation = 7
    }
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "documents" {
  bucket = aws_s3_bucket.documents.id
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_public_access_block" "documents" {
  bucket                  = aws_s3_bucket.documents.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# The SPA build. Publico NUNCA: no desenho o unico lector é o CloudFront via OAC
# (origin access control) — ver cloudfront.tf.
resource "aws_s3_bucket" "site" {
  # #checkov:skip=CKV_AWS_18:Bucket do site sem access logging — todo o tráfico passa pelo
  # CloudFront, que loga na sua própria distribuição (ADR 0019)
  # #checkov:skip=CKV_AWS_21:Versioning desligado — o build vive no CI e o bucket é
  # regenerável com um commit (ver ADR 0019)
  # #checkov:skip=CKV_AWS_144:Sem replicação entre regiões — o build reside no CI e o
  # bucket é regenerável com um commit (ver ADR 0019)
  # #checkov:skip=CKV_AWS_145:SSE-S3 (AES256) de propósito, não KMS (ver ADR 0019)
  # #checkov:skip=CKV2_AWS_61:Sem lifecycle no bucket do site — o build é regenerável,
  # expirar objetos antigos não poupa nada aqui (ver ADR 0019)
  # #checkov:skip=CKV2_AWS_62:Sem notificações de eventos no bucket do site — não há
  # eventos a processar (ver ADR 0019)
  bucket = "${var.project_name}-site"
}

resource "aws_s3_bucket_server_side_encryption_configuration" "site" {
  bucket = aws_s3_bucket.site.id
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_public_access_block" "site" {
  bucket                  = aws_s3_bucket.site.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}