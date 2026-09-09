# Handoff — Etapa 02: Upload com URL pré-assinado

**Data:** 2026-09-09 · **Sessão:** #3 · **Estado:** completa

## O que ficou feito

- **Pacote `com.docgrid.storage`** — a abstração do armazenamento:
  - `StorageService` (`backend/src/main/java/com/docgrid/storage/StorageService.java`) —
    porta: `createUploadUrl`, `createDownloadUrl`, `exists`. Mecanismo, não política.
  - `S3StorageService` — implementação sobre `S3Client` + `S3Presigner`. Só o presigner
    emite URLs; o ficheiro nunca passa por aqui.
  - `StorageConfig` — monta os clientes a partir de `StorageProperties`. **Orientado por
    propriedades, não por perfil**: com `docgrid.storage.endpoint` definido aponta ao
    LocalStack (credenciais estáticas, acesso por caminho); sem ele, AWS + cadeia de
    credenciais por omissão. Os métodos `buildS3Client` / `buildS3Presigner` são estáticos
    e package-private para os testes montarem o cliente igual à aplicação.
  - `PresignedUrl`, `StorageProperties` — records.
- **Registo do documento + emissão de URL** — `com.docgrid.document`:
  - `DocumentUploadService` — valida (tipo aceite, extensão coerente com o `Content-Type`,
    `0 < tamanho ≤ 10 MB`), gera a chave determinística
    `org/{orgId}/{ano}/{mes}/{documentId}.{ext}` (via `Clock`), regista o `Document` em
    `UPLOADED` e escreve o evento `CREATED` **na mesma transação**, e só então pede o URL
    pré-assinado de escrita.
  - `DocumentUploadController` — `POST /api/documents/upload-url` (201),
    `GET /api/documents/{id}/file-url` (200; 404 se o documento não existir).
  - `dto/UploadUrlRequest` (com Bean Validation), `dto/UploadUrlResponse`,
    `dto/FileUrlResponse`.
  - `UploadValidationException extends DomainException`.
  - `DocumentExceptionHandler` (`@RestControllerAdvice`) — `UploadValidationException`→400,
    `DocumentNotFoundException`→404, como `ProblemDetail`. `spring.mvc.problemdetails.enabled`
    passa os erros de `@Valid` a `application/problem+json`. **RFC 7807 a sério é a etapa 06.**
  - `Document.forUpload(...)` — fábrica nova: a chave do S3 deriva do próprio id (que
    existe antes do `flush`). O construtor de 5 args mantém-se só para os testes que fixam
    a chave.
  - `DocumentEvent.created(documentId, actor)` — o evento `CREATED`, primeiro consumidor
    do valor do enum que existia desde a etapa 01. `to_status = UPLOADED`, sem `from`.
  - `UploadProperties` — `@ConfigurationProperties("docgrid.upload")`: `url-ttl` (5m),
    `max-file-size` (10MB), `allowed-types` (mapa `Content-Type → extensão`).
- **Identidade de sessão** — `com.docgrid.auth`:
  - `CurrentUserProvider` — interface: `currentOrganizationId()`, `currentUserId()`. A
    etapa 06 acrescenta a implementação baseada no token JWT e apaga a de demonstração.
  - `DevBootstrap` (`@Profile("local")`) — semeia uma organização + utilizador de
    demonstração no arranque (idempotente pelo email `dev@docgrid.local`), regista os ids
    no log, e serve de `CurrentUserProvider` guardando esses ids.
  - `CurrentUserConfig` — `CurrentUserProvider` por omissão (`@ConditionalOnMissingBean`)
    que deixa a aplicação arrancar mas **recusa alto** qualquer pedido que precise do
    utilizador. Sem isto, o contexto não sobe no perfil `aws` (antes da etapa 06) nem num
    teste de contexto.
- **Configuração** — `DocGridApplication` passou a `@ConfigurationPropertiesScan` (apanha
  `StorageProperties` e `UploadProperties`, e as futuras, sem `@EnableConfigurationProperties`
  espalhado). Blocos `docgrid.storage` / `docgrid.upload` em `application.yml`; `-local`
  aponta ao LocalStack; `-aws` sem endpoint nem credenciais fixas.
- **`docker-compose.yml`** — `S3_SKIP_SIGNATURE_VALIDATION: "0"` no LocalStack. Sem isto,
  o LocalStack ignora a assinatura e a expiração dos URLs pré-assinados.
- **Dependências** (`backend/pom.xml`) — BOM do AWS SDK v2 (`2.54.13`), `software.amazon.awssdk:s3`
  (inclui o `S3Presigner`), `org.testcontainers:localstack` (teste, gerido pelo Spring Boot
  em `1.21.4`).
- **ADRs** — `docs/adr/0005-upload-com-url-pre-assinado.md`,
  `docs/adr/0006-documentos-orfaos.md`. `docs/03-CONVENTIONS.md` ganhou o pacote `storage/`.

## Decisões tomadas

| Decisão | Alternativa posta de lado | Porquê |
|---|---|---|
| URL pré-assinado **`PUT`** | `POST` com política | O AWS SDK para Java v2 não constrói o `POST` com política (só JS/Python); seria montar a policy base64 e a assinatura HMAC à mão. A única vantagem — limite de tamanho rígido — obtém-se validando o tamanho declarado. ADR 0005. |
| **Hash e tamanho do ficheiro adiados para a etapa 03** | Endpoint de confirmação nesta etapa | Sem SQS nem worker, seria um segundo pedido só para o servidor ler o objeto e hashear. A etapa 03 já lê o objeto para o processar. Desvio ao âmbito do briefing, com autorização. ADR 0005. |
| **Documentos órfãos: só desenho, sem código de limpeza** | Job `@Scheduled` + transição `UPLOADED → REJECTED` | Limpeza a sério exige mexer na máquina de estados que a etapa 01 fechou, e não é "barata". Recomendação: regra de ciclo de vida do S3 no Terraform (etapa 11). ADR 0006. |
| `CurrentUserProvider` + `DevBootstrap` (`@Profile("local")`) a servir de provider | Cabeçalhos `X-Org-Id`/`X-User-Id`; provider separado com consulta à BD por pedido | A interface é a costura que a etapa 06 precisa de qualquer forma. `DevBootstrap` juntar semear e servir evita ir à BD a cada pedido para o utilizador fixo, e a etapa 06 apaga a classe inteira. |
| `StorageConfig` orientado por `docgrid.storage.endpoint`, sem `@Profile` nos beans | Beans `@Profile("local")` / `@Profile("aws")` | O perfil `test` não é nenhum dos dois. Uma propriedade (endpoint presente ⇒ LocalStack) cobre os três casos e os testes injetam-na pelo container. |
| Provider por omissão que arranca mas recusa (`CurrentUserConfig`) | App não arrancar sem identidade; `ApplicationContextTest` importar uma config de identidade | Mantém o teste de contexto da etapa 00 intacto e o perfil `aws` a arrancar. O pedido falha com mensagem clara, não com o contexto a não subir. |
| Testes de integração nomeados `*Test` (surefire), não `*IT` (failsafe) | Ativar o `maven-failsafe-plugin` | A etapa 00 já estabeleceu `*Test` + `mvnw verify` + um só runner. Introduzir failsafe é uma mudança de infra fora do âmbito. |
| `StorageService` com duplo de teste em `DocumentUploadServiceTest` | LocalStack em todos os testes de upload | O S3 a sério está em `S3StorageServiceTest` e `DocumentUploadFlowTest`. O teste do serviço cobre o registo e a validação; levantar o LocalStack aí não provava nada de novo e custava 10 s. |
| `@ConfigurationPropertiesScan` em `DocGridApplication` | `@EnableConfigurationProperties(X.class)` por classe | Uma declaração para todas as `@ConfigurationProperties` atuais e futuras. |

ADRs escritos: `docs/adr/0005-upload-com-url-pre-assinado.md`,
`docs/adr/0006-documentos-orfaos.md`

## Revisão de segurança

O `/security-review` (plugin e comando) não corre neste repositório: assume um `main` ou
um `origin` e aqui só há `master`, sem remoto. Foi feita uma revisão manual do diff
`59d3f12..HEAD`. Conclusões:

- **`GET /api/documents/{id}/file-url` e `POST /api/documents/upload-url` estão abertos** —
  sem autenticação nem isolamento por organização. É IDOR à espera de acontecer: qualquer
  UUID devolve um URL de leitura assinado. **Não é regressão** — não existe autenticação
  nenhuma no projeto ainda. É o primeiro item que a etapa 06 fecha (`findByIdAndOrganizationId`
  já existe no repositório para isso).
- **O tamanho é declarado pelo cliente, não imposto.** O `PUT` pré-assinado aceita
  qualquer tamanho. A etapa 03 apanha o excesso ao ler o objeto. Documentado no ADR 0005.
- **O `Content-Type` é afirmado pelo cliente**, sem verificação de magic bytes (o ficheiro
  não passa pelo servidor). Mitigado: o URL de leitura serve sempre como anexo
  (`Content-Disposition: attachment`), nunca inline; o Textract (etapa 04) recusa o que
  não for fatura.
- **`original_filename`** é guardado sem separadores de caminho nem caracteres de controlo,
  mas não escapado para HTML — o frontend (etapa 07) tem de o escapar (o React fá-lo por
  omissão).
- **"Bucket não público"** depende de Block Public Access, que o LocalStack comunitário
  não impõe. Fica no Terraform da etapa 11. O que os testes provam é o mecanismo da
  assinatura (URL expirado e assinatura adulterada → 403).
- **`DevBootstrap`** semeia um `password_hash` falso e inutilizável; a etapa 06, ao
  remover a classe, tem de tratar de credenciais reais.

Nenhum achado é bloqueante ou corrigível dentro do âmbito da etapa 02 — as lacunas com
forma de autenticação são o território explícito da etapa 06.

## Como verificar

Testes e formatação (o Testcontainers levanta Postgres e LocalStack; precisa do Docker
Desktop a correr):

```bash
npm run lint
# BUILD SUCCESS

npm test
# Tests run: 126, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS
```

Distribuição real dos 16 testes novos: 6 em `DocumentUploadServiceTest`, 5 em
`S3StorageServiceTest`, 4 em `DocumentUploadFlowTest`, 1 em `DevBootstrapTest`. Durante a
corrida continuam a aparecer as 4 linhas `ERROR ... duplicate key` esperadas da etapa 01.

O fluxo completo por `curl` (o critério de aceitação nº 1). `npm run up` fica em primeiro
plano; noutro terminal (os ids da organização/utilizador de demonstração aparecem no log
do arranque, linha `Demonstração local: organização … · utilizador …`):

```bash
RESP=$(curl -s -XPOST localhost:8080/api/documents/upload-url \
  -H 'content-type: application/json' \
  -d '{"filename":"fatura.pdf","contentType":"application/pdf","sizeBytes":46}')
echo "$RESP"
#  -> {"documentId":"…","storageKey":"org/…/2026/09/….pdf","uploadUrl":"http://localhost:4566/…",
#      "httpMethod":"PUT","requiredHeaders":{"Content-Type":"application/pdf"},"expiresAt":"…"}

# extrair uploadUrl e documentId do RESP, depois:
curl -s -o /dev/null -w '%{http_code}\n' --upload-file fatura.pdf \
  -H 'content-type: application/pdf' "<uploadUrl>"
#  -> 200

docker exec docgrid-localstack awslocal s3 ls --recursive s3://docgrid-documents
#  -> org/<orgId>/2026/09/<documentId>.pdf   (com o tamanho certo)

curl -s localhost:8080/api/documents/<documentId>/file-url
#  -> {"url":"http://localhost:4566/…?X-Amz-…","expiresAt":"…"}
curl -s "<url de leitura>" -o saida.pdf && diff fatura.pdf saida.pdf   # iguais

# tipo não permitido -> 400 application/problem+json com mensagem clara
curl -s -i -XPOST localhost:8080/api/documents/upload-url -H 'content-type: application/json' \
  -d '{"filename":"x.exe","contentType":"application/x-msdownload","sizeBytes":10}'
#  -> HTTP/1.1 400 · Content-Type: application/problem+json
#     {"...","detail":"Tipo de ficheiro não aceite: application/x-msdownload. Aceites: application/pdf, image/jpeg, image/png",...}

# assinatura adulterada -> 403 (troca X-Amz-Signature=... por zeros no URL de leitura)
```

Estes comandos foram executados nesta sessão (a app correu no porto 8090 por já haver
uma instância no 8080) e deram exatamente estas respostas.

## O que ficou por fazer

Nada em falta bloqueia a etapa 03. Fora de âmbito por decisão:

- **`size_bytes` e `file_hash` ficam nulos.** As colunas e `Document.recordUploadedFile`
  existem, à espera. Quem os preenche é a etapa 03 (o worker lê o objeto; o evento do S3
  traz o tamanho). Sem o hash, a regra do duplicado binário da etapa 05 não funciona até lá.
- **Notificação S3 → SQS, worker, `UPLOADED → PROCESSING`, idempotência, DLQ** — etapa 03.
  A `docker-compose` já cria a fila `docgrid-document-processing`; falta a DLQ e a
  notificação de eventos do bucket.
- **Autenticação, papéis, isolamento por organização, RFC 7807 completo, OpenAPI** —
  etapa 06. `CurrentUserProvider` é a costura; `DevBootstrap` e `CurrentUserConfig` são
  para apagar/substituir aí.
- **Limpeza de órfãos** — só desenho (ADR 0006); regra de ciclo de vida do S3 na etapa 11.
- **Interface de upload com progresso** — etapa 07.

## Armadilhas para a próxima sessão

1. **Chaves de mapa com `/` em `@ConfigurationProperties` precisam de `"[...]"`.** Em
   `application.yml`, `allowed-types` tem de ser `"[application/pdf]": pdf`. Sem os
   parênteses retos e as aspas, o Spring remove a barra (`application/pdf` → `applicationpdf`)
   e o tipo nunca é encontrado — o upload de um PDF válido dá 400. Custou meio depuração.
2. **O LocalStack só valida assinatura e expiração com `S3_SKIP_SIGNATURE_VALIDATION=0`.**
   Está no `docker-compose.yml` e nas duas configurações de container de teste
   (`S3StorageServiceTest`, `LocalStackContainerConfiguration`). Sem isso, um URL expirado
   devolve 200 e o teste `refusesAnExpiredUrl` falha. Se `npm run infra` não recriar o
   container depois de puxar estas alterações, força com `npm run down && npm run infra`.
3. **O LocalStack comunitário serve objetos sem autenticação, mesmo com signature
   validation ligada e com Block Public Access / bucket policy aplicados.** Um `GET`
   anónimo a um objeto devolve 200. Não há como provar "bucket não público" contra o
   LocalStack — o teste prova o mecanismo da assinatura (expirado e adulterado → 403). A
   garantia real é Block Public Access, no Terraform da etapa 11.
4. **`org.testcontainers:localstack` tem coordenadas diferentes na linha 2.x.** O Spring
   Boot 3.5.16 gere o Testcontainers em `1.21.4`, onde o módulo é `org.testcontainers:localstack`.
   No cache `~/.m2` há um `testcontainers-localstack:2.0.5` (coordenadas novas da linha 2.x) —
   **não é esse**. Não sobrepor a versão.
5. **`S3Client.builder()` devolve `S3ClientBuilder` (tipo de topo), `S3Presigner.builder()`
   devolve `S3Presigner.Builder` (aninhado).** Não há um `S3Client.Builder`. `.forcePathStyle(true)`
   existe no builder do cliente mas não no do presigner — nos dois usa-se
   `serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())`.
6. **A aplicação não arranca no perfil `aws` até a etapa 06.** `CurrentUserConfig` deixa o
   contexto subir, mas `POST /upload-url` e `GET /file-url` respondem 500
   (`UnsupportedOperationException` "Sem identidade de sessão…"). É esperado: a etapa 06
   (autenticação) vem antes da 11 (deploy AWS).
7. **Mais um contexto Spring, mais containers.** `DocumentUploadFlowTest` é um
   `@SpringBootTest` com Postgres **e** LocalStack, configuração diferente de
   `ApplicationContextTest` — o Spring não partilha o contexto, portanto são dois Postgres
   e um LocalStack a mais na corrida. `LocalStackContainerConfiguration` mantém os testes
   de S3 num só contexto de propósito; não a dividir.
8. **`DevBootstrap` implementa `ApplicationRunner` e `CurrentUserProvider`.** Em testes de
   `@SpringBootTest` que precisem da identidade, importa-se `DemoIdentityConfiguration`
   (`com.docgrid.auth`, fontes de teste), que o regista como bean `@Primary` — o Spring
   corre-lhe o `run` no arranque e ele semeia.
9. **`spring.mvc.problemdetails.enabled: true` é global.** Mudou o formato de **todas** as
   respostas de erro para `application/problem+json`. O corpo `{"status":"UP"}` do
   `/actuator/health` não é afetado (é sucesso), e o `ApplicationContextTest` continua a
   passar — mas qualquer teste novo que afirme sobre um corpo de erro tem de contar com
   `type`/`title`/`status`/`detail`/`instance`.

## Ficheiros centrais desta etapa

- `backend/src/main/java/com/docgrid/storage/StorageService.java` — a porta. Se houver um
  ficheiro para ler antes dos outros neste pacote, é este.
- `backend/src/main/java/com/docgrid/storage/StorageConfig.java` — como os clientes do S3
  se montam (endpoint presente ⇒ LocalStack), e os `build*` estáticos que os testes reusam.
- `backend/src/main/java/com/docgrid/document/DocumentUploadService.java` — validação,
  geração da chave, registo + evento `CREATED` na mesma transação, emissão do URL.
- `backend/src/main/java/com/docgrid/document/DocumentUploadController.java` — os dois
  endpoints.
- `backend/src/main/java/com/docgrid/auth/DevBootstrap.java` — semeia e serve a identidade
  de demonstração; a etapa 06 apaga-o.
- `backend/src/main/java/com/docgrid/auth/CurrentUserConfig.java` — a rede de segurança
  que deixa a app arrancar sem identidade.
- `backend/src/main/resources/application.yml` — blocos `docgrid.storage` / `docgrid.upload`,
  `problemdetails`.
- `backend/src/test/java/com/docgrid/support/LocalStackContainerConfiguration.java` — o
  container de S3 como bean, com `DynamicPropertyRegistrar` a injetar o endpoint.
- `backend/src/test/java/com/docgrid/document/DocumentUploadFlowTest.java` — o fluxo
  `curl`-equivalente ponta a ponta.
- `docs/adr/0005-upload-com-url-pre-assinado.md` — PUT vs POST, e porque o hash foi adiado.

## Commits

```
8e36c02 build(backend): SDK da AWS v2 e Testcontainers LocalStack
b87facc feat(storage): abstração StorageService com implementação S3
7bf6b54 feat(auth): identidade de sessão com implementação de demonstração local
110d981 feat(document): registo de documento e emissão de URL de upload
10ab297 feat(document): endpoints de upload e leitura, e tradução mínima de erros
c22d6f4 fix(auth): a aplicação arranca sem identidade de sessão configurada
8372038 docs(adr): upload com URL pré-assinado e estratégia para documentos órfãos
```
