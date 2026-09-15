# 0019 — Infraestrutura como desenho

**Estado:** aceite · **Data:** 2026-09-15

## Contexto

O briefing da etapa 11 assume uma conta AWS que não existe.  
Não foi criada e não será usada.  
A etapa fica interrompida por decisão.  
O documento `docs/04-ROADMAP.md` previa este corte.  
O trabalho permanece local e documenta a arquitetura AWS pretendida.

## Decisão

O Terraform é escrito e validado, mas nunca aplicado.  
O sinal técnico valorizado no briefing permanece no repositório.  
Fica a um `apply` de ser real, sem custo e sem nada para gerir.  
O objetivo é manter a prova de conceito sem criar recursos.

## O que valida

- Sintaxe e tipos: `terraform validate`.
- Formatação coerente: `terraform fmt`.
- Suite de segurança estática: `checkov`, `tflint`, `shellcheck`, `hadolint`, `gitleaks`.
- Coerência interna do design: a fila espelha o script do LocalStack.
- Artefactos revisáveis por qualquer humano.

## O que não valida

- Se a AWS aceita os recursos de verdade: sem `apply` não há prova.
- Preços reais na conta.
- Se o `user-data` arranca de verdade: o `shellcheck` valida o texto, não a execução.
- O `terraform plan`: chama `sts:GetCallerIdentity` e os prefix lists do CloudFront.

## O que seria preciso para aplicar

- Conta AWS com faturação ativa.
- `terraform init` com backend S3 real: substituir o bucket literal em `versions.tf`.
- `terraform plan`.
- `terraform apply`.
- Verificação manual das pontas: health, upload, fila, alarmes.
- No final da demonstração, `terraform destroy`: configuração principal primeiro, bootstrap por último.

## Observações aceites

Cada `#checkov:skip` tem a sua razão. Lista de aceitações:

- Sem flow logs: CloudTrail e SSM cobrem o rasto.
- Security group por omissão da VPC sem uso.
- IP público na instância: sem NAT, sem IP elástico.
- Saída livre no security group da aplicação: a fronteira é a entrada.
- Versioning desligado nos buckets de conteúdo: o bucket de estado mantém versioning.
- Sem access logging nos buckets: o tráfego passa pelo CloudFront.
- Sem replicação entre regiões.
- Chaves KMS geradas pela AWS em vez de CMK: SSM, S3, ECR, SNS, logs.
- RDS sem backups, sem exportação de logs, sem snapshots.
- Autenticação por palavra-passe no SSM, não por IAM.
- `deletion_protection` com valor `false`: o destroy deve remover tudo.
- Performance Insights e Enhanced Monitoring desligados: são pagos.
- Sem Multi-AZ.
- Sem WAF: o custo é por pedido.
- Sem access logging na distribuição.
- Sem origin failover.
- Sem geo restriction.
- TLS 1.2 ou superior por plataforma: certificado por omissão.
- Sem response headers policy.
- Retenção de logs a 7 dias.
- `db-url` e `db-user` como String no SSM.

## Desvios por limitação do provider

A política gerida do SSM Session Manager não passa na validação de ARN do provider Terraform.  
A política é anexada com um comando CLI durante o `apply`:

```bash
aws iam attach-role-policy --policy-arn arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore
```

## Consequências

Custo zero absoluto e nada para gerir.  
No dia em que exista conta, o trabalho será aplicar, verificar e destruir.  
Este ADR serve de registo da revisão de segurança que o briefing exige como critério de aceitação.