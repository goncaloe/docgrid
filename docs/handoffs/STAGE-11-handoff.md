# Handoff — Etapa 11: IaC, CI/CD e infraestrutura como desenho
**Data:** 2026-09-15 · **Sessão:** #15 · **Estado:** completa
**Caminho:** duas sessões (planear / implementar)

## O que ficou feito
- Imagem do backend (backend/Dockerfile): multi-stage com um jar em camadas, utilizador não root (UID 1001), heap limitada (-XX:MaxRAMPercentage=40 -XX:+UseSerialGC), HEALTHCHECK contra /actuator/health/readiness. Scripts image:build e image:run em package.json. Verificada de facto: o container arranca com o compose local e o HEALTHCHECK do Docker chega a 'healthy'.
- Sondas de liveness e readiness (application.yml, HealthProbesTest): grupo readiness com readinessState,db; s3/sqs/extractor ficam fora do grupo. SecurityConfig alargado para /actuator/health/** para que a sonda possa aceder sem token (os detalhes seguem após when-authorized).
- Terraform completo em infra/terraform: bootstrap (bucket de estado remoto com versionamento+SSE+privado, alerta de orçamento de 5 EUR com SNS por email), rede (VPC sem NAT, endpoint gateway de S3, SGs mínimos), S3 (documentos+site), SQS+DLQ espelhando o script do LocalStack, RDS Postgres 16, SSM Parameter Store, instância t4g.micro com user-data que instala Docker, lê os segredos e arranca o compose e o Caddy via systemd, IAM mínimo, ECR com ciclo de vida, CloudFront com duas origens e X-Origin-Verify, observabilidade (log group de 7 dias, alarmes de DLQ e instância). README do infra. NUNCA aplicado (sem conta AWS).
- CI/CD (.github/workflows): ci.yml com quatro jobs (backend, frontend, infra, security com gitleaks+CodeQL), image.yml multi-arquitetura (amd64+arm64) que publica em ghcr.io por digest com o GITHUB_TOKEN. Dependabot, .tflint.hcl, .hadolint.yaml, .gitleaks.toml (allowlist apenas para a fixture de testes do frontend).
- Documentação: ADRs 0017-0019, docs/COSTS.md (~26 USD/mês estimados); correção de AGENTS.md (EC2 t4g.micro com referência ao ADR 0017), 02-ARCHITECTURE.md (tabela de ambientes + secção Ambiente AWS-alvo + parágrafo api/worker), 04-ROADMAP.md e README.md (badges de CI, puxar a imagem do ghcr.io, secção honesta sobre o estado do deploy).

## Decisões tomadas
| Decisão | Alternativa | Justificação |
|---|---|---|
| Terraform escrito e validado, nunca aplicado | Aplicar numa conta AWS; não escrever Terraform | Sem conta AWS não há custo nem gestão; o sinal técnico fica a um apply de ser real (ADR 0019) |
| EC2 t4g.micro + RDS | ECS Fargate | Custo ~11 USD/mês contra ~25 USD; orçamento de memória 1 GB; escolha do autor mantida após duas comparações (ADR 0017) |
| Rede sem NAT nem endpoints de interface | NAT Gateway (~32 USD/mês); cinco endpoints (~36-73 USD/mês) | A conta inverte-se a esta escala; só entra o endpoint gateway de S3, gratuito |
| CloudFront com duas origens (S3 + EC2 em /api/*) | Domínio de API à parte com CORS; ALB com ACM | Zero linhas de React alteradas, zero CORS no Spring, HTTPS com o certificado por omissão |
| SSM Session Manager + IAM mínimo | Chave SSH com SG restrito | Sem porta 22 aberta; a política gerida AmazonSSMManagedInstanceCore é anexada por CLI no apply (o provider rejeita o ARN) |
| SSM Parameter Store (SecureString) | Secrets Manager | 0,40 USD/segredo/mês contra grátis; os valores vivem no tfstate, por isso o bucket de estado é privado e encriptado (ADR 0018) |
| Registo real ghcr.io, ECR só no desenho | Só ECR; só ghcr.io | ghcr.io grátis em repositório público com o GITHUB_TOKEN; ECR documenta a peça (ADR 0018) |
| Imagem multi-arquitetura (amd64+arm64) | Só amd64 | O Graviton pede arm64 e quem clona corre amd64/Apple Silicon |
| .terraform.lock.hcl versionado | Continuar a ignorá-lo | Fixa versões e checksums de providers |

ADRs escritos: docs/adr/0017-computacion-e-rede-en-aws.md, docs/adr/0018-fronteira-registo-e-segredos.md, docs/adr/0019-infraestrutura-como-deseno.md

## Desvios ao plano
Plano seguido: docs/plans/STAGE-11-plano.md

**9 detalhes de execução · 0 decisões que obrigaram a parar · 1 problema só apanhado na verificação manual**

1. SecurityConfig só permitia o caminho exato /actuator/health; a sonda anónima dava 401. Alargado para /actuator/health/** (detalhes após when-authorized).
2. HealthProbesTest: dois casos (326 testes no total, não 325); o novo contexto custa ~17 s, longe do limite de 2 min.
3. ApplicationContextTest fixava a string exata do corpo do health e falhou com o campo groups; agora valida o contrato (estado UP/DOWN).
4. Provider 6.64: redrive_policy como string JSON; random_password com override_special (RDS rejeita /, " e @).
5. Política gerida do SSM Session Manager por CLI no apply (validação de ARN do provider).
6. Forma v6 de aws_cloudfront_distribution (blocos origin, custom_origin_config, viewer_certificate, default_root_object).
7. Templates: $${} para variáveis do script; diretiva shellcheck disable=SC2034,SC2154; ficheiros em LF.
8. Comentário falso de perfis AWS em application.yml corrigido (um só processo aws,worker).
9. Gitleaks: 2 detecções devidas a uma fixture JWT de MSW (frontend/src/test/handlers.ts); allowlist de caminho, não regra global.
(problema fora dos testes) A sonda anónima: os testes que verificam a readiness são autenticados (para ver componentes); o requisito 'sem token' só apareceu ao verificar a imagem com o curl.

## Como verificar
```bash
npm test              # 326/326 verdes
npm run lint          # Spotless limpo
cd frontend && npm run lint && npm run typecheck && npm test && npm run build   # 60 testes, build ok
terraform -chdir=infra/terraform init -backend=false && terraform -chdir=infra/terraform validate
terraform -chdir=infra/terraform/bootstrap init -backend=false && terraform -chdir=infra/terraform/bootstrap validate
tflint --chdir=infra/terraform        # 0 avisos
checkov -d infra/terraform            # 0 detecções
shellcheck infra/terraform/templates/user-data.sh.tftpl docker/localstack/init/ready.d/*.sh
hadolint -c .hadolint.yaml backend/Dockerfile
gitleaks detect --config .gitleaks.toml --no-banner
npm run infra && npm run image:build
docker run --rm -p 8081:8080 -e SPRING_PROFILES_ACTIVE=local -e DOCGRID_S3_ENDPOINT=http://host.docker.internal:4566 -e DOCGRID_SQS_ENDPOINT=http://host.docker.internal:4566 -e SPRING_DATASOURCE_URL=jdbc:postgresql://host.docker.internal:5432/docgrid docgrid:local
curl -s localhost:8081/actuator/health/readiness   # {"status":"UP"}
```
Resultado esperado: 326/326 testes verdes, lint limpo, terraform validate válido nas duas configurações, tflint/checkov/hadolint/shellcheck/gitleaks limpos, imagem com o HEALTHCHECK 'healthy'.

Nota: a porta 8080 estava ocupada por outra aplicação local (não DocGrid) nesta sessão; a verificação é feita na porta 8081. O script npm run image:run usa a porta 8080 por omissão.

## O que ficou por fazer
- Etapa 12 (montra): dados de demonstração, vídeo, GIFs, diagramas. Não bloqueia.
- No GitHub: os workflows ci e image foram lançados no commit final; verificar que ficam verdes (a verificação local já cobre o que eles verificam).
- Sem conta AWS: o terraform apply/destroy, o deploy e o URL público ficam fora por decisão (ADR 0019) — sessão avulsa no dia em que houver conta.

## Armadilhas para a próxima sessão
- A porta 8080 desta máquina pode estar ocupada por outra aplicação; verificar antes de npm run image:run.
- templatefile renderiza qualquer ${...}: as variáveis do shell são escapadas como $${VAR} no .tftpl.
- O shellcheck rejeita CRLF (SC1017): os ficheiros .sh/.tftpl novos devem ser guardados em LF (o .gitattributes normaliza ao fazer commit).
- hadolint 2.15 marca DL3025 no HEALTHCHECK (comando shell obrigatório) e DL3059 nos dois RUN do Dockerfile (a cache de camadas é deliberada); ambos estão em .hadolint.yaml.
- checkov conta os CKV mencionados nos comentários de skip como 'detecções' ao fazer grep: filtrar por 'FAILED for resource', não por 'CKV'.
- O provider AWS 6.64 rejeita o ARN arn:aws:iam::policy/...: a política gerida do SSM é anexada por CLI (comando documentado em iam.tf e README do infra).
- gitleaks marca a fixture JWT de MSW como leak; o allowlist está em .gitleaks.toml por caminho.
- No Windows, o PATH das ferramentas winget é separado com ';' (não ':'); python user Scripts não está no PATH por omissão.
- sort/checkov falham sem TMPDIR existente no Windows (D:/tmp não existe); TMPDIR para um diretório local.

## Ficheiros centrais desta etapa
- backend/Dockerfile — imagem multi-stage com jar em camadas e HEALTHCHECK na readiness.
- backend/src/test/java/com/docgrid/HealthProbesTest.java — a sonda que os containers usam.
- infra/terraform/cloudfront.tf — a fronteira única: duas origens, X-Origin-Verify, CORS dos documentos.
- infra/terraform/iam.tf — privilégio mínimo + nota da política gerida por CLI.
- infra/terraform/queue.tf — a fila que espelha o LocalStack.
- infra/terraform/templates/user-data.sh.tftpl — tudo o que a instância faz no primeiro arranque.
- infra/terraform/bootstrap/main.tf — bucket de estado e alerta de orçamento antes de qualquer recurso pago.
- docs/COSTS.md — estimativa mensal e como desligar tudo.

## Commits
- eb7c5e7 feat(build): imagem de container do backend
- 735bebf feat(health): probes de liveness e readiness
- 4de8911 feat(infra): bucket de estado remoto e alerta de orçamento
- d943833 feat(infra): rede da VPC sem NAT nem endpoints de interface
- 2df9af8 feat(infra): S3, SQS com DLQ e RDS Postgres
- e9565b8 feat(infra): instância, IAM mínimo e CloudFront como fronteira única
- 294136c feat(infra): log group, alarmes e ponto de entrada da documentação
- 52f8f3f ci: testes, análise estática, Terraform e deteção de segredos
- 05afeb7 ci: imagem multi-arquitetura publicada no ghcr.io
- b64898a docs(adr): ambiente AWS, fronteira de entrega e infraestrutura como desenho
- 0a5ad78 docs: ambiente AWS-alvo e estado real do deploy