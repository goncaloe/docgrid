#!/usr/bin/env bash
# Corre dentro do LocalStack quando este fica pronto. Cria os recursos que as etapas
# seguintes esperam encontrar: o bucket dos documentos, a fila de processamento, a sua
# dead-letter queue, e a notificação que liga um ao outro. Isto é a versão local do que
# o Terraform fará em AWS na etapa 11 — mesmo desenho, escala menor.
set -euo pipefail

BUCKET="${DOCGRID_BUCKET:-docgrid-documents}"
QUEUE="${DOCGRID_QUEUE:-docgrid-document-processing}"
DLQ="${DOCGRID_DLQ:-docgrid-document-processing-dlq}"
REGION="${AWS_DEFAULT_REGION:-eu-west-1}"
ACCOUNT="000000000000" # o LocalStack usa sempre esta conta

# Idempotente de propósito: o script volta a correr em cada arranque do container, e
# falhar porque o recurso já existe deixaria o healthcheck --wait pendurado para sempre.
s3api() { awslocal s3api "$@"; }
sqs() { awslocal sqs "$@"; }

if ! s3api head-bucket --bucket "${BUCKET}" 2>/dev/null; then
    s3api create-bucket \
        --bucket "${BUCKET}" \
        --region "${REGION}" \
        --create-bucket-configuration LocationConstraint="${REGION}"
fi

if ! sqs get-queue-url --queue-name "${DLQ}" 2>/dev/null | grep -q QueueUrl; then
    # A DLQ retém os ficheiros para sempre (14 dias, o máximo do SQS): uma mensagem que
    # cá pára só desaparece por decisão de alguém.
    sqs create-queue --queue-name "${DLQ}" \
        --attributes MessageRetentionPeriod=1209600
fi
DLQ_URL=$(sqs get-queue-url --queue-name "${DLQ}" --query QueueUrl --output text)
DLQ_ARN="arn:aws:sqs:${REGION}:${ACCOUNT}:${DLQ}"

if ! sqs get-queue-url --queue-name "${QUEUE}" 2>/dev/null | grep -q QueueUrl; then
    sqs create-queue --queue-name "${QUEUE}"
fi
QUEUE_URL=$(sqs get-queue-url --queue-name "${QUEUE}" --query QueueUrl --output text)
QUEUE_ARN="arn:aws:sqs:${REGION}:${ACCOUNT}:${QUEUE}"

# Coerência com o tempo esperado de processamento (docs/adr/0008): a extração demora
# 5 a 30 s, portanto 120 s de invisibilidade dão margem sem fazer esperar um retry.
# O maxReceiveCount define quando a mensagem desiste e vai para a DLQ.
sqs set-queue-attributes --queue-url "${QUEUE_URL}" --attributes "
{
  \"VisibilityTimeout\": \"120\",
  \"RedrivePolicy\": \"{\\\"deadLetterTargetArn\\\":\\\"${DLQ_ARN}\\\",\\\"maxReceiveCount\\\":\\\"3\\\"}\",
  \"Policy\": \"{\\\"Version\\\":\\\"2012-10-17\\\",\\\"Statement\\\":[{\\\"Effect\\\":\\\"Allow\\\",\\\"Principal\\\":{\\\"Service\\\":\\\"s3.amazonaws.com\\\"},\\\"Action\\\":\\\"sqs:SendMessage\\\",\\\"Resource\\\":\\\"${QUEUE_ARN}\\\"}]}\"
}"

# O evento `s3:ObjectCreated:*` na fila: é disto que o worker vive. Sem a notificação, o
# ficheiro subiu e ninguém sabe.
if ! s3api get-bucket-notification-configuration --bucket "${BUCKET}" 2>/dev/null | grep -q '"Queue"'; then
    s3api put-bucket-notification-configuration --bucket "${BUCKET}" --notification-configuration "
{
  \"QueueConfigurations\": [
    {
      \"QueueArn\": \"${QUEUE_ARN}\",
      \"Events\": [\"s3:ObjectCreated:*\"]
    }
  ]
}"
fi

echo "DocGrid: bucket '${BUCKET}', filas '${QUEUE}' (+ DLQ) e notificação S3→SQS prontos em ${REGION}."