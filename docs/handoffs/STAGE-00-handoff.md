# Handoff — Etapa 00: Fundações

**Data:** 2026-09-09 · **Sessão:** #1 · **Estado:** completa

## O que ficou feito

- **Projeto Maven** em `backend/pom.xml`: Spring Boot 3.5.16, Java 21, com web, JPA,
  validation, actuator, Flyway (`flyway-core` + `flyway-database-postgresql`) e driver
  Postgres. Maven Wrapper em `backend/mvnw`, `backend/mvnw.cmd`, `backend/.mvn/`.
- **Esqueleto de pacotes por funcionalidade** em `backend/src/main/java/com/docgrid/`:
  `document`, `extraction`, `validation`, `supplier`, `export`, `auth`, `shared`, cada um
  com um `package-info.java` a dizer o que lá vai viver. Só `DocGridApplication.java`
  tem código.
- **Ambiente local** em `docker-compose.yml`: Postgres 16 e LocalStack 4.9.2, ambos com
  healthcheck. `docker/localstack/init/ready.d/01-create-resources.sh` cria no arranque o
  bucket `docgrid-documents` e a fila `docgrid-document-processing` em `eu-west-1`.
- **Comandos do projeto** em `package.json` (raiz): `up`, `infra`, `down`, `test`, `lint`,
  `format`, `logs`. `scripts/mvnw.mjs` reencaminha para o Maven Wrapper escolhendo o
  executável da plataforma e carrega o `.env` quando existe. **Não há `Makefile`.**
- **Perfis Spring**: `application.yml` (comum), `application-local.yml` (containers do
  compose), `application-aws.yml` (sem valores por omissão, de propósito) e
  `src/test/resources/application-test.yml`.
- **Flyway** com `backend/src/main/resources/db/migration/V1__init.sql`, vazia.
- **Formatação** com Spotless 3.10.2 + palantir-java-format 2.98.0, ligada à fase
  `validate`: código mal formatado falha o build.
- **Teste de integração** `backend/src/test/java/com/docgrid/ApplicationContextTest.java`
  com Testcontainers.
- **Ficheiros de raiz**: `.editorconfig`, `.gitattributes`, `.env.example`, `README.md`,
  `frontend/.gitkeep`.
- **Documentação**: `docs/` e `stages/` deixaram de estar ignorados e passaram a ser
  versionados; todas as referências a `make` foram substituídas pelos comandos npm e a
  secção de commits das convenções passou para português.

## Decisões tomadas

| Decisão | Alternativa posta de lado | Porquê |
|---|---|---|
| Comandos por npm scripts na raiz | `Makefile` com GNU Make instalado; Taskfile; `dg.ps1` + `dg.sh` | O `make` não existe no Windows nem vem com o Git for Windows. O Node já é pré-requisito do frontend, portanto não acrescenta nada a instalar. O Taskfile resolveria o multiplataforma, mas continua a ser um binário a instalar. |
| Compose só com infraestrutura; aplicação no host | Aplicação em container com Dockerfile multi-stage | Cumpriria o critério à letra e adiantava a etapa 11, mas cada alteração de código passaria a exigir reconstrução de imagem. O Dockerfile faz falta em produção e escreve-se na etapa 11. |
| Commits em português | Inglês, como diziam as convenções | Regra 5 do `AGENTS.md`: português nas explicações. Corrigidos `docs/03-CONVENTIONS.md` e os dois procedimentos em `.agents/prompts/`, que diziam o contrário. Tipo e âmbito continuam em inglês. |
| LocalStack fixo em `4.9.2` | A imagem mais recente (`2026.08.2`) | As imagens `2026.xx` exigem `LOCALSTACK_AUTH_TOKEN` e saem com código 55 sem licença. A `4.9.2` corre offline, sem credenciais e sem custo. |
| Teste só com Postgres, sem container LocalStack | Postgres + LocalStack, como sugerem as convenções | Nenhum código Java fala com S3 ou SQS nesta etapa — o SDK entra na etapa 02. Levantar o LocalStack num teste que nada testa custa tempo e não prova nada. A criação do bucket e da fila verifica-se pelo compose. |
| Spotless + palantir-java-format | google-java-format | Indentação de 4 espaços (o google usa 2, que destoa de Spring) e sem o atrito de `--add-exports` em JDK 21. |
| Sem probes de liveness e readiness | `management.endpoint.health.probes.enabled: true` | Acrescentam `"groups":[...]` ao corpo do `/health` e quebram o critério literal `{"status":"UP"}`. São preocupação da etapa 11, com o ECS. |
| Perfis só `local`, `test` e `aws` | Criar já `api` e `worker` | São outro eixo — papel do processo, não ambiente. Só fazem sentido na etapa 03, quando existir um worker. |
| `package-info.java` em cada pacote | `.gitkeep` | O Git não versiona pastas vazias, e um `package-info` documenta o pacote em vez de ser um ficheiro morto. |
| `docs/` e `stages/` versionados | Continuarem ignorados | O `.gitignore` escondia os ADRs, e um ADR que ninguém lê não serve o propósito que as convenções lhe dão. |

ADRs escritos: `docs/adr/0002-ambiente-local-e-comandos.md`

## Como verificar

Precisas do Docker Desktop a correr. Todos estes comandos foram corridos nesta sessão.

```bash
npm run up
```

Fica em primeiro plano. Noutro terminal:

```bash
curl localhost:8080/actuator/health
# {"status":"UP"}

docker exec docgrid-localstack awslocal s3 ls
# 2026-09-09 14:31:07 docgrid-documents

docker exec docgrid-localstack awslocal sqs list-queues
# http://sqs.eu-west-1.localhost.localstack.cloud:4566/000000000000/docgrid-document-processing

docker exec docgrid-postgres psql -U docgrid -d docgrid -tAc "select version, description, success from flyway_schema_history;"
# 1|init|t
```

Testes e formatação não precisam do compose — o Testcontainers levanta o seu próprio Postgres:

```bash
npm test
# Tests run: 2, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS

npm run lint
# BUILD SUCCESS
```

Ciclo do zero:

```bash
npm run down && npm run up
```

Verificado também um `git clone` para uma pasta limpa, com a infraestrutura a arrancar a
partir do clone — foi assim que se apanhou o problema das pontas de linha.

## O que ficou por fazer

Nada em falta bloqueia a etapa 01. Fora de âmbito por decisão, não por esquecimento:

- **`Dockerfile` do backend** — etapa 11 (ECS Fargate). Consequência de a aplicação correr
  no host: não há, nesta etapa, prova de que ela arranca em container.
- **CI (GitHub Actions)** — etapa 11.
- **DLQ do SQS** — etapa 03, com o worker. O script de init só cria a fila principal.
- **SDK da AWS no código Java** — etapa 02. O LocalStack está de pé, mas nada em Java lhe fala.
- **Frontend** — etapa 07. Só existe `frontend/.gitkeep`.
- **`npm run seed`** — etapa 12. Já é referido em `docs/06-DEMO-CHECKLIST.md` e no briefing
  da etapa 12, mas o script não existe.
- **Entidades e tabelas** — etapa 01. `V1__init.sql` está deliberadamente vazia e o
  `ddl-auto` está em `validate`.

## Armadilhas para a próxima sessão

1. **O LocalStack tem de ficar na linha 4.x.** As imagens `2026.xx` saem com
   `exit code 55 · License activation failed`, mesmo só para S3 e SQS. Se alguém
   "atualizar" a imagem no `docker-compose.yml`, é este o sintoma.
2. **Os containers têm `container_name` fixo** (`docgrid-postgres`, `docgrid-localstack`).
   Isso dá comandos `docker exec` previsíveis, mas impede correr dois checkouts do projeto
   ao mesmo tempo.
3. **O Spotless segue a política de pontas de linha do `.gitattributes`.** Quando o
   `.gitattributes` foi acrescentado, o `npm run format` reescreveu a cópia de trabalho de
   CRLF para LF. O conteúdo versionado já era LF, portanto não havia nada para committar,
   mas o `git status` mostrou modificações fantasma até um `git add -u` refrescar o cache
   de estado do índice. Se vires ficheiros "modificados" com `git diff` vazio, é isto.
4. **O teste do `/actuator/health` compara o corpo exato** `{"status":"UP"}`. Depende de
   `show-details: when-authorized` e de as probes estarem desligadas. Ao ativar liveness e
   readiness na etapa 11, o teste tem de ser atualizado ao mesmo tempo.
5. **`npm run up` fica em primeiro plano.** Para trabalhar no IDE, usa `npm run infra` e
   arranca a aplicação a partir de lá — o perfil `local` já aponta para `localhost`.
6. **`application-local.yml` só é versionado por causa de uma exceção explícita** no
   `.gitignore`. A regra genérica continua a proteger os overrides pessoais de quem
   desenvolve; um `application-local.yml` criado noutro sítio será ignorado.
7. **O `spotless:check` corre na fase `validate`**, portanto o `npm test` falha por
   formatação antes de chegar aos testes. `npm run format` corrige.
8. **Sem `.env`, tudo funciona.** O `scripts/mvnw.mjs` carrega o `.env` se existir e o
   compose lê as mesmas variáveis. Os dois usam os mesmos nomes (`POSTGRES_*`,
   `DOCGRID_BUCKET`, `DOCGRID_QUEUE`) para não divergirem.

## Ficheiros centrais desta etapa

- `package.json` — os comandos do projeto; a porta de entrada de quem clona.
- `scripts/mvnw.mjs` — reencaminha para o Maven Wrapper e carrega o `.env`.
- `docker-compose.yml` — Postgres e LocalStack, com healthchecks que esperam pelos recursos.
- `docker/localstack/init/ready.d/01-create-resources.sh` — cria o bucket e a fila. Tem de
  ficar com LF e com bit de execução, garantidos pelo `.gitattributes` e pelo índice.
- `backend/pom.xml` — dependências, Spotless e a ligação à fase `validate`.
- `backend/src/main/resources/application.yml` (e `-local`, `-aws`) — perfis e actuator.
- `backend/src/test/java/com/docgrid/ApplicationContextTest.java` — a prova de que o
  esqueleto arranca contra um Postgres real.
- `.gitattributes` — pontas de linha; sem ele o script do LocalStack não corre no container.

## Commits

```
f894626 chore: ficheiros de raiz e comandos do projeto
e50b499 chore(backend): projeto Maven com Spring Boot 3.5 e Java 21
9946442 chore(infra): docker compose com Postgres e LocalStack
243a68a feat(backend): perfis local, test e aws com Flyway
b6b610f test(backend): arranque do contexto com Testcontainers
f289a9e docs: README com instruções de arranque
8263fa8 chore: normaliza pontas de linha e bit de execução
c350651 docs: versiona a documentação do projeto e os briefings de etapa
```
