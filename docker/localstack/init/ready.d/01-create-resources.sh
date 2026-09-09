#!/usr/bin/env bash
# Corre dentro do LocalStack quando este fica pronto. Cria os recursos que as
# etapas seguintes esperam encontrar: o bucket dos documentos e a fila de
# processamento. A DLQ chega na etapa 03, com o worker.
set -euo pipefail

BUCKET="${DOCGRID_BUCKET:-docgrid-documents}"
QUEUE="${DOCGRID_QUEUE:-docgrid-document-processing}"
REGION="${AWS_DEFAULT_REGION:-eu-west-1}"

awslocal s3api create-bucket \
    --bucket "${BUCKET}" \
    --region "${REGION}" \
    --create-bucket-configuration LocationConstraint="${REGION}"

awslocal sqs create-queue \
    --queue-name "${QUEUE}" \
    --region "${REGION}"

echo "DocGrid: bucket '${BUCKET}' e fila '${QUEUE}' criados em ${REGION}."
