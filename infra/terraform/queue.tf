# Fila de processamento + DLQ. Tem que espelar docker/localstack/init/ready.d/
# docgrid-resources.sh: mesmo desenho, escala menor. Se aqui diverges, o ambiente local
# deixa de representar o alvo.

resource "aws_sqs_queue" "dlq" {
  name = "${var.project_name}-document-processing-dlq"
  # Retenção de 14 dias (1209600 s), o máximo do SQS: uma mensagem que cá para só
  # desaparece por decisão de alguém (igual que no script do LocalStack).
  message_retention_seconds = 1209600
  # SSE com a chave de SQS gerida pela AWS — gratuita (CKV_AWS_27).
  sqs_managed_sse_enabled = true

  tags = { Name = "${var.project_name}-document-processing-dlq" }
}

resource "aws_sqs_queue" "processing" {
  name = "${var.project_name}-document-processing"
  # Coerência com o tempo esperado de processamento (docs/adr/0008): a extração demora 5 a
  # 30 s, 120 s de invisibilidade dão margem sem fazer esperar um retry. O maxReceiveCount
  # define quando a mensagem desiste e vai para a DLQ — tem que bater com a config da app
  # (docgrid.queue.max-receive-count: 3 em application.yml).
  visibility_timeout_seconds = 120
  sqs_managed_sse_enabled    = true

  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.dlq.arn
    maxReceiveCount     = "3"
  })

  # Política que permite ao S3 enviar a notificação de objeto criado — o evento
  # `s3:ObjectCreated:*` do que vive o worker, com a condición aws:SourceArn restringida ao
  # bucket de documentos. É o equivalente do Policy do script do LocalStack (allí non hai
  # condición: o LocalStack non valida condicións).
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect    = "Allow"
        Principal = { Service = "s3.amazonaws.com" }
        Action    = "sqs:SendMessage"
        Resource  = aws_sqs_queue.processing.arn
        Condition = {
          ArnLike = {
            "aws:SourceArn" = "arn:aws:s3:*:*:${var.project_name}-documents"
          }
        }
      }
    ]
  })

  tags = { Name = "${var.project_name}-document-processing" }
}

# A notificação que liga o bucket à fila — o equivalente do put-bucket-notification-configuration.
resource "aws_s3_bucket_notification" "documents" {
  bucket = aws_s3_bucket.documents.id

  queue {
    queue_arn = aws_sqs_queue.processing.arn
    events    = ["s3:ObjectCreated:*"]
  }
}