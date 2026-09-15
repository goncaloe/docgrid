# Plano — Etapa 11: IaC, CI/CD e infraestrutura como desenho

**Data:** 2026-09-15 · **Planeado com:** Opus 5 · **Estado:** aprovado

> Este plano vai ser executado noutra sessão, sem memória da conversa que o produziu.
> O que não estiver aqui, desaparece. Caminhos exatos, não descrições.
>
> E não é um documento morto: quem implementa escreve nele quando ele deixar de bater certo
> com o disco — ver "Desvios durante a execução", no fim.

## Contexto

### O que já existe

A etapa 10 fechou a observabilidade (`docs/handoffs/STAGE-10-handoff.md`): correlação de
ponta a ponta, métricas Prometheus, health checks por dependência (`db`, `s3`, `sqs`,
`extractor`) e logs ECS em JSON no perfil `aws`. A aplicação está completa e testada — 324
testes de backend — e corre em local com `npm run up`.

O que **não** existe e esta etapa não vai criar: qualquer recurso AWS, qualquer deploy,
qualquer URL público.

Confirmado no disco em 2026-09-15:

- `backend/pom.xml` — Spring Boot 3.5.16, Java 21, Spotless na fase `validate`.
- `backend/src/main/resources/application.yml` — perfis, `spring.profiles.group.local: worker`,
  actuator a expor `health,info,prometheus,metrics`, `show-details: when-authorized`.
  O comentário nas linhas do health diz "as probes de liveness e readiness chegam na etapa 11".
- `backend/src/main/resources/application-aws.yml` — sem valores por omissão, de propósito;
  logs ECS.
- `backend/src/main/resources/db/migration/` — 11 migrações, `V1` a `V11`.
- `docker-compose.yml` + `docker/localstack/init/ready.d/docgrid-resources.sh` — a versão
  local do desenho AWS: bucket, fila, DLQ, política de redrive `maxReceiveCount=3`,
  `VisibilityTimeout=120`, CORS e notificação S3→SQS.
- `frontend/` — Vite + React 18, sem base URL configurada: o cliente usa caminhos
  **relativos** (`frontend/src/api/client.ts` linha 87 e 174). Não há CORS no backend.
- `package.json` na raiz — `up`, `infra`, `down`, `test`, `lint`, `format`, `logs`.
- **Não existe** `.github/`, `infra/`, `terraform/`, nem qualquer `Dockerfile`.
- Remoto atual: `debian → git@91.229.245.20:docgrid.git`. Ramo atual: `master`.

### O que mudou face ao briefing

O briefing (`stages/STAGE-11-iac-cicd-aws.md`) assume uma conta AWS a ser usada. **Não vai
haver conta AWS.** A decisão foi tomada e está registada abaixo. Três consequências:

1. O Terraform é escrito e verificado, mas **nunca aplicado**.
2. Não há deploy nem URL público. O registo de imagens real é o `ghcr.io`.
3. Quatro dos oito critérios de aceitação do briefing ficam por cumprir, por decisão, não
   por falha. A tabela honesta está em "Critérios de aceitação".

O `docs/04-ROADMAP.md` prevê este corte com estas palavras: *"Se tiveres de cortar, corta a
09 e a 11 (fica em local, documenta a arquitetura AWS pretendida)"*. A diferença é que a
arquitetura não fica documentada em prosa — fica em Terraform validado, a um `apply` de
distância.

## O que se vai construir

1. **Terraform completo do ambiente AWS-alvo** em `infra/terraform/`: VPC sem NAT, EC2
   `t4g.micro` com Docker Compose, RDS `db.t4g.micro`, S3, SQS + DLQ, CloudFront com duas
   origens, IAM de privilégio mínimo, SSM Parameter Store, CloudWatch. Mais um
   `infra/terraform/bootstrap/` com o bucket de estado e o alerta de orçamento.
2. **Imagem do backend** (`backend/Dockerfile`), multi-arquitetura, publicada no `ghcr.io`
   e **verificada de facto** a arrancar contra o `docker-compose` local.
3. **GitHub Actions**: `ci.yml` (testes, análise estática, Terraform, segredos, CodeQL) e
   `image.yml` (imagem multi-arquitetura para o `ghcr.io`).
4. **Probes de liveness e readiness** no Spring, com o teste que garante que a readiness
   inclui `db` e **não** inclui `s3`/`sqs`.
5. **Documentação**: `docs/COSTS.md`, três ADRs, e a correção de `AGENTS.md`,
   `docs/02-ARCHITECTURE.md`, `docs/04-ROADMAP.md` e `README.md` para dizerem a verdade
   sobre o estado do deploy.

## Pré-requisitos verificados

| Assume | Confirmado |
|---|---|
| Aplicação completa e testada | `docs/handoffs/STAGE-10-handoff.md`: 324 testes verdes |
| Docker a correr | `docker info` responde (28.3.0) |
| Java 21 | `java -version` → 21.0.11 |
| Node ≥ 20.6 | `node --version` → v24.14.1 |
| `gh` instalado | `gh --version` → 2.92.0 |
| Conta AWS | **Não existe e não vai ser usada.** Decisão registada abaixo. |
| `terraform`, `tflint`, `checkov` | **Não instalados.** Ver "Riscos e pontos de paragem". |
| Repositório GitHub | **Não existe.** Passo 0, e é o utilizador que o cria. |

## Decisões tomadas

| Decisão | Alternativa posta de lado | Porquê |
|---|---|---|
| **Terraform escrito e validado, nunca aplicado** | Aplicar numa conta AWS; aplicar e destruir por ciclo de demonstração; não escrever Terraform nenhum | O utilizador desistiu de usar serviços AWS. Sem `apply`, o custo é zero absoluto e não há nada a gerir; o Terraform mantém-se como o sinal técnico que o briefing valoriza, e fica a um comando de ser real. Não o escrever perderia o item central da etapa. |
| **EC2 `t4g.micro` + RDS** como desenho documentado | ECS Fargate | Escolha do utilizador, mantida depois de lhe ter apresentado a comparação duas vezes. O ADR 0017 faz a comparação a sério — que é literalmente o que o briefing pede: *"Pesa e justifica — a justificação vale mais que a escolha."* |
| **Subnets públicas com IP público, sem NAT e sem endpoints de interface** | NAT Gateway (~$32/mês); endpoints de interface para ecr.api, ecr.dkr, logs, sqs, secretsmanager (~$36–73/mês) | A poupança que o briefing assume **inverte-se a esta escala**: cinco endpoints de interface custam mais do que o NAT que substituiriam. Só entra o endpoint *gateway* de S3, que é grátis. A segurança fica nos security groups. |
| **CloudFront com duas origens**: S3 por omissão, EC2 em `/api/*` | Domínio de API à parte com CORS no Spring; ALB com certificado ACM | O frontend mantém os caminhos relativos `/api` que já usa — **zero linhas de React alteradas** — o Spring continua sem configuração de CORS, e há HTTPS sem domínio próprio nem ACM. |
| **SSM Session Manager**, porta 22 fechada | Chave SSH com SG restrito a um IP | Sem porta aberta, sem chave para perder, acesso auditado no CloudTrail. O agente já vem no Amazon Linux 2023. |
| **SSM Parameter Store** (SecureString) | Secrets Manager, como o briefing pede | $0,40 por segredo por mês × 2 contra **grátis**. Faz o mesmo trabalho a esta escala, com a chave KMS gerida pela AWS, também gratuita. Desvio ao briefing, aprovado. |
| **Flyway no arranque** | Tarefa de migração dedicada | O desenho tem **um só processo** a correr `aws,worker`. Não há corrida entre serviços; a tarefa dedicada resolvia um problema que este desenho não tem. |
| Health check do ALB/Caddy em **`/actuator/health/readiness`**, grupo `readinessState,db` | `/actuator/health` | A etapa 10 deixou o health a 503 quando o S3 ou o SQS falham. Usar isso como probe mataria um container saudável por causa de um soluço do SQS. |
| **Terraform descreve ECR; o registo real é o `ghcr.io`** | Só ECR; só ghcr.io | ECR é o que um desenho AWS usa e são 15 linhas. O `ghcr.io` é o registo que existe de facto para este projeto e é gratuito em repositório público. O ADR 0018 regista a distinção para não parecer incoerência. |
| **Sem Dockerfile de frontend** | Container de nginx a servir o SPA | O briefing pede as duas coisas e elas contradizem-se: se o SPA vai para S3 + CloudFront, um container de nginx é peso morto. O build vive no CI. Desvio ao briefing, aprovado. |
| **Imagem multi-arquitetura** (amd64 + arm64) | Só amd64 | O desenho documentado usa Graviton (`t4g`), portanto precisa de arm64; quem clonar o repositório corre amd64 ou Apple Silicon. Em repositório público os dois runners nativos são gratuitos. |
| Estado remoto em S3 com **`use_lockfile`** | Tabela DynamoDB para os locks | Bloqueio nativo do S3 desde o Terraform 1.10. Menos um recurso a criar, a pagar e a destruir. |
| **Bucket de estado e orçamento em `bootstrap/`**, com estado local | Tudo numa só configuração | Resolve o ovo-e-a-galinha do estado remoto, e põe o alerta de orçamento de 5 € ativo **antes de existir um único recurso pago** — que é a ordem que o briefing exige. |
| **`.terraform.lock.hcl` versionado** | Continuar a ignorá-lo, como está hoje no `.gitignore` | É o ficheiro que fixa as versões e os checksums dos providers. Ignorá-lo faz cada máquina resolver uma versão diferente — é o oposto do que um lockfile serve. |
| **Repositório público** | Privado | Desbloqueia minutos ilimitados de Actions, CodeQL, runners ARM e `ghcr.io` gratuito, e é o que faz sentido num portefólio. Se ficar privado: caem o CodeQL e o runner ARM (a imagem passa a amd64) e ficam 2 000 min/mês. |

**ADRs a escrever:**
- `docs/adr/0017-computacao-e-rede-em-aws.md` — EC2 `t4g.micro` contra Fargate com os números
  dos dois, o orçamento de memória de 1 GB e o que ele obriga, a rede sem NAT com a conta
  feita, SSM em vez de SSH. Menciona multi-região, auto-scaling e blue/green como evolução,
  como o briefing pede.
- `docs/adr/0018-fronteira-registo-e-segredos.md` — CloudFront com duas origens (porque não
  há CORS nem domínio), header de verificação mais prefix list, CORS do bucket de documentos,
  ECR no desenho contra `ghcr.io` no CI, Parameter Store em vez de Secrets Manager.
- `docs/adr/0019-infraestrutura-como-desenho.md` — porque é que esta infraestrutura está
  escrita e não aplicada, o que isso valida e o que **não** valida, e o que seria preciso
  para a aplicar.

## Riscos e pontos de paragem

Lê isto **antes** do primeiro passo. Acrescenta-lhe as perguntas novas antes de parar.

- **`terraform`, `tflint` e `checkov` não estão instalados nesta máquina.** Instala-os
  (`winget install Hashicorp.Terraform`, `winget install TFLint.TFLint`, `pipx install checkov`
  ou os binários) → **se a instalação falhar ou exigir privilégios que não tens, para e
  pergunta**, em vez de escrever Terraform que nunca foi validado. Escrever HCL sem o
  conseguir validar localmente é o modo de falha mais provável desta etapa.

- **Versão major do provider da AWS.** O plano assume `~> 6.0`. Se o `terraform init` resolver
  outra major, **usa a que ele resolver**, commita o `.terraform.lock.hcl`, e regista em
  "Desvios". Não fixes uma versão que não conseguiste descarregar.

- **`terraform plan` não vai correr, e isso é esperado.** O provider da AWS chama
  `sts:GetCallerIdentity` ao configurar-se; sem credenciais nem `plan` arranca. `validate` é
  o teto do que se verifica offline. **Não inventes credenciais nem `mock_provider` para
  forçar um `plan`** — se te parecer que precisas disso, para e pergunta.

- **`data "aws_ec2_managed_prefix_list"`** (a lista do CloudFront) é um data source que
  contacta a AWS. Não afeta o `validate`, afeta o `plan`. É esperado.

- **O `checkov` vai apontar coisas que este desenho aceita de propósito**: instância em subnet
  pública, RDS sem Multi-AZ, sem backups, CloudFront sem WAF, bucket sem versionamento.
  Trata-as com `#checkov:skip=CKV_AWS_XXX: <razão>` inline **e** uma linha no ADR 0019 por
  cada uma. **Nunca desligues o checkov inteiro para o fazer passar.** Se aparecer uma
  observação que não caiba nesta lista e que mude o desenho, **para e pergunta**.

- **CodeQL para Java corre um build.** O `autobuild` chama Maven, e o Spotless está ligado à
  fase `validate` — se houver formatação por corrigir, o CodeQL falha por uma razão que nada
  tem a ver com segurança. Usa um passo de build manual com `-Dspotless.check.skip=true`.

- **`gitleaks-action` pede licença** para organizações. Em conta pessoal é gratuito, mas se
  pedir chave, **instala o binário e corre-o diretamente** em vez de usares a action. Não
  ponhas uma licença em segredo do repositório por causa disto.

- **`HealthProbesTest` pode criar um contexto Spring novo** e fazer a suite crescer. As
  armadilhas 1, 2 e 4 do handoff da etapa 10 aplicam-se todas: `@AutoConfigureObservability`
  é obrigatório, MockMvc manual não corre o Spring Security, e `/actuator/health` já não é
  garantidamente 200. **Se a suite crescer mais de ~2 minutos por causa deste teste**, troca-o
  por um teste de binding de propriedades e regista em "Desvios".

- **Migração do repositório.** Criar o repositório GitHub, torná-lo público e fazer o primeiro
  push são ações do **utilizador**, não tuas. **Não corras `gh repo create` nem `git push`
  para um remoto novo sem ele to pedir explicitamente** nessa sessão.

- **Não implementes nada da etapa 12.** Dados de demonstração, vídeo, GIFs e diagramas são de
  lá. Esta etapa toca no `README.md` só para corrigir o que ficaria falso.

## Passos de implementação

Por ordem. Cada passo é uma unidade de commit.

### Passo 0 — Migração para o GitHub (ação do utilizador)

Não é um commit. O utilizador cria o repositório público, e só depois:

```bash
git branch -m master main
git remote add origin git@github.com:<utilizador>/docgrid.git
git push -u origin main
```

O remoto `debian` fica como segundo remoto. **Se o repositório ainda não existir quando
chegares aqui, continua pelos passos seguintes** — só o CI precisa do GitHub, e só para
correr de facto.

---

### Passo 1 — Imagem do backend

`backend/Dockerfile` (criar), `backend/.dockerignore` (criar), `package.json` (alterar).

- Multi-stage. Fase de build: `maven:3.9-eclipse-temurin-21` (ou `eclipse-temurin:21-jdk` +
  o wrapper `backend/mvnw`), `mvn -B -DskipTests package`. Fase de runtime:
  `eclipse-temurin:21-jre-alpine`.
- **Jar em camadas**: `java -Djarmode=tools -jar app.jar extract --layers --destination
  extracted` na fase de build, e copiar `dependencies/`, `spring-boot-loader/`,
  `snapshot-dependencies/`, `application/` em `COPY` separados. Sem isto, cada alteração de
  código reconstrói a camada das dependências inteira.
- Utilizador não-root (`adduser -S -u 1001 docgrid`), `USER docgrid`.
- `ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=40 -XX:+UseSerialGC"` — o desenho corre numa
  máquina de 1 GB; ver o orçamento de memória no ADR 0017.
- `HEALTHCHECK` a bater em `/actuator/health/readiness`.
- `.dockerignore`: `target/`, `.git/`, `node_modules/`, `*.md`.
- `package.json`: `"image:build": "docker build -t docgrid:local backend"` e
  `"image:run"` a correr a imagem no perfil `local` contra o compose que já existe
  (`--network host` não funciona em Docker Desktop no Windows: usa
  `-e DOCGRID_S3_ENDPOINT=http://host.docker.internal:4566`, o mesmo para o SQS, e
  `SPRING_DATASOURCE_URL` apontado a `host.docker.internal:5432`).
- **Verificação obrigatória deste passo**: `npm run infra && npm run image:build && npm run
  image:run`, e `curl localhost:8080/actuator/health` a responder. A imagem tem de arrancar
  mesmo — é a única parte desta etapa que se prova a correr.
- Commit: `feat(build): imagem de container do backend`

### Passo 2 — Probes de liveness e readiness

`backend/src/main/resources/application.yml` (alterar),
`backend/src/main/resources/application-aws.yml` (alterar),
`backend/src/test/java/com/docgrid/HealthProbesTest.java` (criar).

- Em `application.yml`, dentro de `management.endpoint.health`:
  ```yaml
  probes:
    enabled: true
  group:
    readiness:
      include: readinessState,db
  ```
  E **apaga o comentário** "As probes de liveness e readiness chegam na etapa 11, com o ECS."
  — passou a ser falso.
- `s3`, `sqs` e `extractor` ficam **fora** do grupo de readiness, de propósito: o health
  continua a reportá-los em `/actuator/health`, mas não derrubam a probe.
- `application-aws.yml`: nada a acrescentar se a configuração comum chegar; confirma e não
  dupliques.
- `HealthProbesTest` — `@SpringBootTest` + `@AutoConfigureObservability` +
  `@AutoConfigureMockMvc`, autenticado como `ADMIN`, a bater em `/actuator/health/readiness`
  e a afirmar: `components` contém `db`; `components` **não** contém `s3` nem `sqs`.
  Reutiliza `backend/src/test/java/com/docgrid/support/` como as outras classes de integração.
- Commit: `feat(health): probes de liveness e readiness`

### Passo 3 — Terraform: base, estado e orçamento

`infra/terraform/bootstrap/main.tf` (criar), `infra/terraform/bootstrap/variables.tf` (criar),
`infra/terraform/bootstrap/README.md` (criar), `.gitignore` (alterar).

- `bootstrap/` tem **estado local** e é aplicado uma vez, antes de tudo o resto. Cria:
  - bucket S3 do estado, com versionamento ligado, encriptação SSE-S3 e bloqueio total de
    acesso público;
  - `aws_budgets_budget` mensal de 5 EUR com notificações a 50%, 80% e 100% (real e previsto);
  - tópico SNS e subscrição por email para essas notificações.
- `.gitignore`: remove a linha `.terraform.lock.hcl` da secção `# Terraform`. As linhas
  `*.tfstate`, `*.tfstate.*` e `*.tfvars` ficam.
- `bootstrap/README.md`: a ordem (`bootstrap` primeiro, sempre), e que o seu estado é local
  e não é versionado.
- Commit: `feat(infra): bucket de estado remoto e alerta de orçamento`

### Passo 4 — Terraform: rede

`infra/terraform/versions.tf`, `providers.tf`, `variables.tf`, `outputs.tf`, `network.tf`
(todos criar), `infra/terraform/terraform.tfvars.example` (criar).

- `versions.tf`: `required_version = ">= 1.10"` (o `use_lockfile` precisa disso),
  `required_providers` com `hashicorp/aws ~> 6.0` e `hashicorp/random`. Bloco
  `backend "s3"` com `bucket`, `key`, `region` e `use_lockfile = true`.
- `providers.tf`: provider `aws` com a região da variável e `default_tags`
  (`Project = "docgrid"`, `ManagedBy = "terraform"`, `Environment = "demo"`).
- `network.tf`: VPC `10.0.0.0/16` com `enable_dns_hostnames` e `enable_dns_support`; IGW;
  uma subnet **pública** `10.0.0.0/24`; duas subnets **privadas** (`10.0.10.0/24`,
  `10.0.11.0/24`) em AZ diferentes — o RDS exige um subnet group com duas AZ, mesmo em
  single-AZ; tabela de rotas pública com `0.0.0.0/0 → IGW`; tabela privada **sem rota por
  omissão**; endpoint *gateway* de S3 associado às duas tabelas.
- Security groups: `app` (entrada 80 **só** do prefix list
  `com.amazonaws.global.cloudfront.origin-facing`, saída tudo) e `db` (entrada 5432 **só**
  do SG `app`, sem saída).
- `variables.tf`: `project_name`, `region` (omissão `eu-west-1`), `instance_type`
  (`t4g.micro`), `db_instance_class` (`db.t4g.micro`), `db_allocated_storage` (20),
  `image_reference`, `alert_email`, `log_retention_days` (7).
- Commit: `feat(infra): rede da VPC sem NAT nem endpoints de interface`

### Passo 5 — Terraform: armazenamento, fila e base de dados

`infra/terraform/storage.tf`, `queue.tf`, `database.tf`, `parameters.tf` (todos criar).

- `storage.tf`: dois buckets — documentos e site — ambos com bloqueio total de acesso
  público e SSE-S3. Regra de ciclo de vida no de documentos a expirar objetos (é um ambiente
  de demonstração, não um arquivo).
- `queue.tf`: fila principal e DLQ. **Tem de espelhar
  `docker/localstack/init/ready.d/docgrid-resources.sh`**: `VisibilityTimeout = 120`,
  `maxReceiveCount = 3` (o mesmo valor de `docgrid.queue.max-receive-count` em
  `application.yml`), retenção da DLQ 1209600 s. Política da fila a permitir
  `sqs:SendMessage` ao serviço `s3.amazonaws.com` com condição `aws:SourceArn` no bucket de
  documentos. `aws_s3_bucket_notification` com `s3:ObjectCreated:*`.
  **Se divergires do script do LocalStack, o ambiente local deixa de representar o alvo** —
  escreve o desvio.
- `database.tf`: subnet group sobre as duas subnets privadas; `aws_db_instance` Postgres 16,
  `db.t4g.micro`, 20 GB gp2, `storage_encrypted = true` (chave gerida pela AWS, gratuita),
  `multi_az = false`, `publicly_accessible = false`, `backup_retention_period = 0`,
  `skip_final_snapshot = true`, `deletion_protection = false`, Performance Insights
  desligado (paga-se).
- `parameters.tf`: `random_password` para o segredo JWT e para a palavra-passe da base;
  `aws_ssm_parameter` SecureString em `/docgrid/jwt-secret` e `/docgrid/db-password`, e
  String em `/docgrid/db-url` e `/docgrid/db-user`. **Regista no ADR 0018 que estes valores
  ficam no tfstate** — é por isso que o bucket de estado é privado e encriptado.
- Commit: `feat(infra): S3, SQS com DLQ e RDS Postgres`

### Passo 6 — Terraform: computação, IAM e fronteira

`infra/terraform/compute.tf`, `iam.tf`, `ecr.tf`, `cloudfront.tf` (todos criar),
`infra/terraform/templates/user-data.sh.tftpl`, `compose.yml.tftpl`, `Caddyfile.tftpl`
(todos criar).

- `iam.tf`: role e instance profile da instância, com a política gerida
  `AmazonSSMManagedInstanceCore` e uma política inline de privilégio mínimo — S3
  `GetObject`/`PutObject`/`DeleteObject` **só nos objetos do bucket de documentos**, SQS
  `ReceiveMessage`/`DeleteMessage`/`GetQueueAttributes`/`ChangeMessageVisibility`/`SendMessage`
  nas duas filas, `textract:AnalyzeExpense`, `ssm:GetParameter*` em `/docgrid/*`,
  `kms:Decrypt` em `alias/aws/ssm`, pull do ECR e escrita no log group. Nada de `*` em
  `Resource` fora do que o obrigar.
- `compute.tf`: AMI do Amazon Linux 2023 **arm64** pelo parâmetro público
  `/aws/service/ami-al2023-latest/al2023-ami-kernel-default-arm64`; `aws_instance`
  `t4g.micro` na subnet pública com `associate_public_ip_address = true`; volume raiz gp3
  10 GB encriptado; `metadata_options` com `http_tokens = "required"` (IMDSv2);
  `user_data = templatefile(...)` e `user_data_replace_on_change = true` — a máquina é gado,
  não há estado nela.
  **Sem IP elástico**: o ambiente nasce e morre, o Terraform lê `public_dns` e é isso que
  alimenta a origem do CloudFront.
- `templates/user-data.sh.tftpl`: `set -euo pipefail`; ficheiro de swap de 2 GB; instalar
  Docker e o plugin do Compose; ler os parâmetros SSM com `--with-decryption` e escrever
  `/opt/docgrid/.env` com permissões `600`; escrever `compose.yml` e `Caddyfile`;
  `docker compose up -d`; unidade systemd para sobreviver a reinício.
- `templates/compose.yml.tftpl`: serviço `app` (imagem da variável, perfil `aws,worker`,
  driver de logs `awslogs`, healthcheck em `/actuator/health/readiness`) e serviço `caddy`
  (porta 80).
- `templates/Caddyfile.tftpl`: recusa com 403 quem não trouxer o header `X-Origin-Verify`
  correto, bloqueia `/actuator/*` na borda, e faz `reverse_proxy app:8080`.
- `ecr.tf`: repositório com `scan_on_push` e **política de ciclo de vida a guardar as últimas
  5 imagens** — sem isto cada push deixa ~300 MB a acumular.
- `cloudfront.tf`: OAC para o bucket do site; distribuição com **duas origens** — S3 (comportamento
  por omissão) e a instância como origem personalizada (`http-only`, com o header
  `X-Origin-Verify` gerado por `random_password`); comportamento ordenado para `/api/*` com
  cache desligado e todos os headers, cookies e query strings encaminhados (políticas
  geridas `Managed-CachingDisabled` e `Managed-AllViewerExceptHostHeader`); respostas de erro
  403 e 404 reescritas para `/index.html` com 200, para o encaminhamento do SPA;
  `price_class = "PriceClass_100"`; certificado por omissão do CloudFront.
  Por fim, `aws_s3_bucket_cors_configuration` no bucket **de documentos** a permitir
  `https://${aws_cloudfront_distribution.this.domain_name}` em `PUT`, `GET` e `HEAD` — é o
  upload direto do browser. Não há ciclo de dependências: site → distribuição → CORS dos
  documentos.
- Commit: `feat(infra): instância, IAM mínimo e CloudFront como fronteira única`

### Passo 7 — Terraform: observabilidade

`infra/terraform/observability.tf` (criar), `infra/terraform/README.md` (criar).

- Log group `/docgrid/app` com `retention_in_days` da variável (7). **Sem retenção, os logs
  ficam para sempre e pagam-se para sempre.**
- Tópico SNS e subscrição por email.
- Alarme em `ApproximateNumberOfMessagesVisible >= 1` na **DLQ** — coerente com a decisão da
  etapa 10 de que a DLQ avisa mas não derruba o health.
- Alarme em `StatusCheckFailed` da instância.
- `infra/terraform/README.md`: **começa por dizer que esta infraestrutura nunca foi
  aplicada**. Depois, o que ela levantaria, a ordem (`bootstrap/` primeiro), os comandos, e
  um ponteiro para `docs/COSTS.md` e para o ADR 0019.
- Commit: `feat(infra): log group, alarmes e ponto de entrada da documentação`

### Passo 8 — Integração contínua

`.github/workflows/ci.yml` (criar), `.github/dependabot.yml` (criar), `.tflint.hcl` (criar),
`.gitleaks.toml` (criar), `.hadolint.yaml` (criar).

`ci.yml`, em `push` para `main` e em `pull_request`, com quatro jobs paralelos:

- **backend** — `actions/setup-java@v4` (temurin 21, `cache: maven`), `npm test` e
  `npm run lint` a partir da raiz. Os Testcontainers correm sem truques: o `ubuntu-latest`
  traz Docker. Põe `timeout-minutes: 30`.
- **frontend** — `actions/setup-node@v4` com `cache: npm` e
  `cache-dependency-path: frontend/package-lock.json`; `npm ci`, `npm run lint`,
  `npm run typecheck`, `npm test`, `npm run build`.
- **infra** — `hashicorp/setup-terraform`; `terraform -chdir=infra/terraform init -backend=false`,
  `validate`, `fmt -check -recursive`; o mesmo para `infra/terraform/bootstrap`; `tflint`;
  `checkov`; `shellcheck` em `infra/terraform/templates/user-data.sh.tftpl` e em
  `docker/localstack/init/ready.d/*.sh`; `hadolint` em `backend/Dockerfile`.
- **security** — `gitleaks` em todo o histórico, e CodeQL para `java` e
  `javascript-typescript`. **Build manual do Java com `-Dspotless.check.skip=true`**, não
  `autobuild` (ver "Riscos").
- `dependabot.yml`: ecossistemas `maven` (em `/backend`), `npm` (em `/frontend`),
  `github-actions` (em `/`) e `docker` (em `/backend`), semanal.
- Commit: `ci: testes, análise estática, Terraform e deteção de segredos`

### Passo 9 — Publicação da imagem

`.github/workflows/image.yml` (criar).

- Em `push` para `main` e em `workflow_dispatch`. `permissions: { contents: read,
  packages: write }` — a autenticação ao `ghcr.io` é com o `GITHUB_TOKEN`, **não há segredo
  a gerir**.
- Dois jobs de build nativos — `ubuntu-latest` (amd64) e `ubuntu-24.04-arm` (arm64) — cada um
  a publicar por digest, e um job final com `docker buildx imagetools create` a compor o
  manifesto. Sem QEMU: compilar arm64 emulado leva 8 a 10 minutos.
- Etiquetas: `latest` e o SHA do commit.
- **Se o repositório for privado**, o runner ARM não existe: reduz a amd64 e regista em
  "Desvios".
- Commit: `ci: imagem multi-arquitetura publicada no ghcr.io`

### Passo 10 — ADRs e custos

`docs/adr/0017-computacao-e-rede-em-aws.md`, `docs/adr/0018-fronteira-registo-e-segredos.md`,
`docs/adr/0019-infraestrutura-como-desenho.md`, `docs/COSTS.md` (todos criar).

Os três ADRs seguem o formato de `docs/03-CONVENTIONS.md` (Contexto, Decisão, Alternativas
consideradas, Consequências) e o conteúdo está listado em "Decisões tomadas", acima.

`docs/COSTS.md`: a estimativa mensal serviço a serviço a preço de tabela em `eu-west-1`
(EC2 `t4g.micro` ~$6,70 · EBS 10 GB ~$0,80 · IPv4 público ~$3,65 · RDS `db.t4g.micro` ~$12,50
· armazenamento do RDS 20 GB ~$2,60 — **total ~$26/mês**); o que é gratuito para sempre a
esta escala (SQS até 1 M pedidos/mês, CloudFront até 1 TB/mês de saída, CloudWatch Logs até
5 GB, orçamentos, SNS, chaves KMS geridas pela AWS); o que se paga por uso desde o primeiro
dia (Textract, ~$0,01 por página); as duas armadilhas que o Terraform fecha (ECR sem política
de ciclo de vida, log group sem retenção); **como desligar tudo** (`terraform destroy`, e o
`bootstrap/` por último); e a nota de que nada disto foi aplicado, com ponteiro para o
ADR 0019. Diz que os preços são de setembro de 2026 e que se confirmam na calculadora.

- Commit: `docs(adr): ambiente AWS, fronteira de entrega e infraestrutura como desenho`

### Passo 11 — Documentação que ficaria falsa

`AGENTS.md`, `docs/02-ARCHITECTURE.md`, `docs/04-ROADMAP.md`, `README.md` (todos alterar).

- `AGENTS.md`, secção "Stack fixa": `ECS Fargate` → `EC2 t4g.micro`, com ponteiro para o
  ADR 0017. **É a única alteração a este ficheiro** — não lhe toques em mais nada.
- `docs/02-ARCHITECTURE.md`:
  - a tabela "Ambientes" — linha `Execução`: `ECS Fargate` → `EC2 t4g.micro com Docker Compose`;
  - secção nova "Ambiente AWS-alvo" com o desenho e a nota, em texto claro, de que **não está
    aplicado**;
  - e a frase sobre api e worker escalarem independentemente passa a dizer que o desenho os
    **suporta** separados mas que este ambiente os corre no mesmo processo (`aws,worker`),
    por custo. Não deixes a promessa antiga de pé — passou a ser falsa.
- `docs/04-ROADMAP.md`: a linha da etapa 11 passa a dizer o que ela entrega de facto.
- `README.md`: badges do CI, como puxar a imagem do `ghcr.io`, e uma secção honesta sobre o
  estado do deploy — **sem URL público, e porquê**. A tabela da stack passa a dizer EC2.
- Commit: `docs: ambiente AWS-alvo e estado real do deploy`

## Critérios de aceitação

```bash
# raiz do repositório
npm test && npm run lint
cd frontend && npm ci && npm run lint && npm run typecheck && npm test && npm run build && cd ..

# infraestrutura
terraform -chdir=infra/terraform init -backend=false
terraform -chdir=infra/terraform validate
terraform -chdir=infra/terraform fmt -check -recursive
terraform -chdir=infra/terraform/bootstrap init -backend=false
terraform -chdir=infra/terraform/bootstrap validate
tflint --chdir=infra/terraform
checkov -d infra/terraform

# scripts, imagem e segredos
shellcheck infra/terraform/templates/user-data.sh.tftpl docker/localstack/init/ready.d/*.sh
hadolint backend/Dockerfile
gitleaks detect --no-banner

# a imagem tem de arrancar mesmo
npm run infra && npm run image:build && npm run image:run
curl -s localhost:8080/actuator/health/readiness    # {"status":"UP"}
```

**Resultado esperado:** 325 testes de backend verdes (os 324 da etapa 10 mais o
`HealthProbesTest`), Spotless e ESLint limpos, `terraform validate` a passar nas duas
configurações, `tflint` sem avisos, `checkov` só com observações cobertas por `skip`
justificado, `shellcheck` e `hadolint` limpos, `gitleaks` sem achados, e a imagem a
responder `UP` na readiness.

E, no GitHub, os dois workflows verdes em `main`, com a imagem visível em
`ghcr.io/<utilizador>/docgrid`.

### Contra o briefing, honestamente

| Critério de `stages/STAGE-11-iac-cicd-aws.md` | |
|---|---|
| `terraform apply` a partir do zero levanta o ambiente inteiro | ✗ por decisão: não há conta AWS |
| `terraform destroy` remove tudo, sem órfãos a faturar | ✗ por decisão |
| Push para `main` faz deploy automático | ~ constrói, testa e publica a imagem; não há destino de deploy |
| O pipeline falha se os testes falharem | ✓ |
| Existe um URL público a funcionar | ✗ por decisão |
| Nenhum segredo no repositório, confirmado com ferramenta | ✓ gitleaks no CI e em local |
| Alerta de orçamento ativo | ✗ escrito em `bootstrap/`, não aplicado |
| Revisão de segurança feita e observações tratadas | ✓ CodeQL, checkov e a revisão escrita no ADR 0019 |

Quatro de oito. **Isto está aprovado e é o âmbito real da etapa** — não é trabalho por
acabar. Quem escrever o handoff diz isto com estas palavras.

## Fora de âmbito

- `terraform apply`, deploy, URL público, alerta de orçamento ativo → só com uma conta AWS.
  Não são etapa 12: são uma sessão avulsa no dia em que houver conta.
- Multi-região, auto-scaling elaborado, blue/green → mencionados como evolução no ADR 0017,
  como o briefing pede.
- Domínio próprio e ACM, WAF, RDS Multi-AZ, backups → fora do desenho, justificado no ADR 0017.
- Dockerfile de frontend → cortado por decisão; o SPA vai para S3 no desenho, e o build vive
  no CI.
- Dados de demonstração, vídeo, GIFs, diagramas → **etapa 12**.
- Ansible ou qualquer provisionamento do servidor `91.229.245.20` → posto de lado; não há
  deploy nenhum nesta etapa.

## Desvios durante a execução

Preenchido por **quem implementa, à medida que acontece** — não no fim, não no handoff.
Quando o plano deixar de bater certo com o disco, corrige-se aqui e o problema fica visível
para quem retomar.

| Passo | O que o plano dizia | O que ficou | Detalhe ou decisão |
|---|---|---|---|
| 1 | HEALTHCHECK e probes assumidos atingíveis sem token | `SecurityConfig` só tornava público `/actuator/health` (caminho exato); `/actuator/health/readiness` dava 401, o que partia o HEALTHCHECK da imagem e as probes do ECS. Alargado o público a `/actuator/health/**`; os detalhes continuam `when-authorized` e o resto do actuator (`prometheus`, `metrics`) continua ADMIN. | Detalhe de execução |
| | | | |
