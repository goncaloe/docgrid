# Etapa 11 — Infraestrutura, CI/CD e AWS

## Objetivo
O projeto em produção na AWS, com infraestrutura em código e deploy automático.

## Porque conta
É a etapa que transforma "projeto de portefólio" em "sabe pôr coisas a correr". Terraform
no repositório é dos sinais mais fortes que podes dar.

**Atenção ao custo.** Configura um alerta de orçamento de 5 € antes da primeira `apply`.
NAT Gateway e RDS fora do free tier queimam dinheiro depressa.

## Contexto a carregar
`docs/02-ARCHITECTURE.md` (ambientes), todos os handoffs anteriores (resumidamente)

## Pré-requisitos
Aplicação completa e testada localmente. Conta AWS com faturação configurada.

## Âmbito
- Terraform: VPC, S3, SQS + DLQ, RDS Postgres, ECS Fargate (api e worker), ALB,
  CloudWatch, Secrets Manager, IAM com privilégio mínimo
- Estado remoto do Terraform em S3 com bloqueio
- Dockerfiles multi-stage para backend e frontend
- GitHub Actions: build, testes com Testcontainers, análise estática, build da imagem,
  push para ECR, deploy
- Migrações Flyway aplicadas no arranque ou por tarefa dedicada (decide)
- Frontend em S3 + CloudFront
- Segredos em Secrets Manager, nunca em variáveis de ambiente do repositório
- Documento `docs/COSTS.md` com a estimativa mensal e como desligar tudo

## Fora
Multi-região, auto-scaling elaborado, blue/green. Menciona como evolução.

## Decisões desta etapa
- Fargate vs uma EC2 t4g.micro com Docker Compose. Fargate impressiona mais; a EC2 custa
  quase nada. Pesa e justifica — a justificação vale mais que a escolha.
- Evitar NAT Gateway com VPC endpoints (poupança relevante)
- RDS ou Postgres em contentor para a demonstração

## Critérios de aceitação
- [ ] `terraform apply` a partir do zero levanta o ambiente inteiro
- [ ] `terraform destroy` remove tudo, sem recursos órfãos a faturar
- [ ] Push para `main` faz deploy automático
- [ ] O pipeline falha se os testes falharem
- [ ] Existe um URL público a funcionar
- [ ] Nenhum segredo no repositório (confirmado com uma ferramenta de deteção)
- [ ] Alerta de orçamento ativo

## Esforço estimado
2 a 3 sessões (rede → serviços → CI/CD, uma por sessão)
