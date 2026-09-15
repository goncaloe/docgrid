# Infraestrutura do DocGrid na AWS

> **Aviso antes de tudo:** esta infraestrutura **nunca foi aplicada**. Está escrita,
> validada e revista, mas não há conta AWS por trás: é um artefacto de desenho, a um
> `apply` de ser real. O ADR 0019 explica o que isto valida e o que **não** valida.
> Não há URL pública, não há deploy, não há nada a ser faturado.

## O que levantaria

Um ambiente de demonstração completo a partir de um único `terraform apply`:

- VPC `10.0.0.0/16` sem NAT: uma subnet pública (a instância) e duas privadas (RDS), um
  endpoint gateway de S3 gratuito nas duas tabelas de rotas, security groups mínimos
  (aplicação: 80 só a partir do CloudFront; base de dados: 5432 só a partir da aplicação).
- RDS Postgres 16 (`db.t4g.micro`, 20 GB, encriptado, sem backups — é demonstração), S3
  (documentos e site, ambos privados e encriptados), fila SQS principal + DLQ a espelhar
  o desenho local (`VisibilityTimeout=120`, `maxReceiveCount=3`).
- Uma instância `t4g.micro` (ARM64, AL2023) a correr o docker compose (aplicação + Caddy);
  o user-data lê os parâmetros do SSM e uma unidade systemd sobrevive aos reinícios.
- CloudFront com duas origens: S3 para o SPA (OAC) e a instância em `/api/*` com
  `X-Origin-Verify`; CORS no bucket de documentos para o upload direto do browser.
- CloudWatch: log group `/docgrid/app` (7 dias), alarmes para a DLQ cheia e para a
  instância morta, com notificação por email.
- `bootstrap/` à parte: o bucket do estado remoto (com versionamento, SSE, privado) e o
  alerta de orçamento de 5 EUR **antes** de qualquer recurso pago.

O custo mensal estimado está na secção de preços de `docs/COSTS.md` (~26 EUR).

## Ordem

`bootstrap` primeiro, sempre — a configuração principal aponta o backend remoto ao bucket
que o bootstrap cria; sem ele, o `terraform init` da configuração principal falha.

```bash
# 1. Bootstrap (estado local, uma única vez)
cd infra/terraform/bootstrap
terraform init
terraform apply          # pede o email do alerta (terraform.tfvars ou -var)

# 2. Configuração principal (estado remoto)
cd infra/terraform
cp terraform.tfvars.example terraform.tfvars   # preencher image_reference e alert_email
terraform init            # usa o backend s3 do versions.tf (substituir o bucket literal)
terraform plan
terraform apply
```

### Enquanto não houver conta AWS — o que se verifica offline

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

O `terraform plan` continua a falhar, e é o esperado: o provider da AWS chama
`sts:GetCallerIdentity` ao arrancar, e o prefixo do CloudFront
(`aws_ec2_managed_prefix_list`) só contacta a AWS durante o plan. Sem credenciais, o
`validate` é o teto. Nunca inventes credenciais nem um `mock_provider` para forçar um plan.

## Aceitações conhecidas desta configuração (ADR 0019)

- Sem NAT: a poupança que o briefing assumia com VPC endpoints inverte-se a esta escala;
  só entra o endpoint gateway de S3, que é gratuito.
- Sem WAF, sem flow logs, sem backups do RDS, sem versionamento nos buckets de conteúdo,
  sem replicação entre regiões: tudo aceite por desenho, com a razão em cada
  `checkov:skip` e consolidado no ADR 0019.
- A política gerida do SSM Session Manager (`AmazonSSMManagedInstanceCore`) não passa na
  validação de ARN do provider do Terraform; anexa-se com um comando de CLI durante o
  `apply`:

  ```bash
  aws iam attach-role-policy --role-name docgrid-instance \
    --policy-arn arn:aws:iam::policy/AmazonSSMManagedInstanceCore
  ```

- O bucket literal no bloco `backend "s3"` do `versions.tf` substitui-se pelo que o
  bootstrap cria (`docgrid-tfstate-<account_id>`).

## Desligar tudo

```bash
cd infra/terraform && terraform destroy   # a configuração principal
cd infra/terraform/bootstrap && terraform destroy   # bucket de estado e alerta, por último
```

Um `terraform destroy` da configuração principal remove tudo o que é faturado; recursos
órfãos seriam uma falha desta etapa (ver o seu briefing).

Atalhos: preços e como desligar tudo em `docs/COSTS.md`; porque é que isto está escrito e
não aplicado, no ADR `docs/adr/0019-infraestrutura-como-desenho.md`.
