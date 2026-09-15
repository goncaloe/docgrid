# 0017 — Computação e rede na AWS

**Estado:** aceite · **Data:** 2026-09-15

## Contexto

O briefing da etapa 11 pede três decisões:
- Fargate contra uma EC2 t4g.micro com Docker Compose.
- Como evitar o NAT Gateway com VPC endpoints.
- RDS ou Postgres em container.

O custo AWS importa: o alerta de orçamento é de 5 EUR ao mês.

## Decisão 1 — EC2 t4g.micro + RDS, não Fargate

Fargate custa ~25 USD de serviços + 0,68 USD EBS + tráfego ao mês.
Tem 0,5 GB de RAM por container.
Dá scale-out, auto-restart e rolling deploys.

A EC2 t4g.micro com Docker Compose custa ~6,70 USD + 0,80 USD EBS + 3,65 USD por IPv4.
Total: ~11 USD ao mês.
Tem 1 GB de RAM.
Executa um processo que acumula as funções de API e worker.

Decisão: EC2.
O orçamento de memória manda.
A JVM do Spring (perfil aws,worker, Flyway no arranque) cabe em 1 GB.
Usa os limites `-XX:MaxRAMPercentage=40` e `-XX:+UseSerialGC` do Dockerfile.

## Decisão 2 — Rede sem NAT e sem endpoints de interface

A conta não fecha a esta escala.
Cinco endpoints de interface custam entre 36 e 73 USD/mês.
Exemplos: ecr.api, ecr.dkr, logs, sqs, secretsmanager.
O NAT Gateway custa ~32 USD/mês.
Usa-se apenas o endpoint de gateway do S3.
É grátis.

As subnets privadas (RDS) não têm rota por omissão.
A segurança está nos security groups.

A instância tem IP público.
Não tem IP elástico.
O ambiente nasce e morre com o Terraform.
A origem do CloudFront usa o `public_dns`.

## Decisão 3 — SSM Session Manager em vez de SSH

Porta 22 fechada.
Sem chaves a perder.
Acesso auditado no CloudTrail.
O agente vem com o Amazon Linux 2023.

A política gerida AmazonSSMManagedInstanceCore aplica-se com um comando CLI durante o apply.
O provider de Terraform rejeita o ARN documentado.
Ver desvios do plano da etapa 11.

Alternativa rejeitada: security group (SG) de SSH restrito a um IP.

## Evolução

Multi-região, dimensionamento automático avançado e blue/green ficam para uma fase futura.
A arquitetura pode migrar para Fargate sem alterar o código.

## Alternativas consideradas

Fargate foi apresentado duas vezes com a comparação de custos.
O autor prefere EC2.

A combinação NAT + endpoints é mais cara do que o NAT isolado.

Postgres em container na instância foi considerado.
RDS custa ~15 USD/mês.
RDS assume backup, failover e gestão fora da máquina.
Mantém-se RDS.

## Consequências

A imagem suporta múltiplas arquiteturas (amd64+arm64).
O Graviton pede arm64.
Quem clona corre em amd64 ou Apple Silicon.
Em registo público, os dois runners nativos são grátis.

RDS db.t4g.micro, 20 GB, encriptado.
Sem backups, sem Multi-AZ, sem Performance Insights.
O ambiente é recriável, não é um artefacto.
As omissões estão cobertas por `#checkov:skip` com justificação.
Estão listadas no ADR 0019.

A probe da instância é `/actuator/health/readiness`.
Contém só `readinessState,db`.
Um soluço do S3/SQS não mata o container.

Todo o tráfego entra por CloudFront (ADR 0018).
A fronteira da rede é o SG.