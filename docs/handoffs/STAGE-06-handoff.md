# Handoff — Etapa 06: API REST e autenticação

**Data:** 2026-09-11 · **Sessão:** #7 · **Estado:** completa

## Contexto de arranque

A etapa 05 deixou o motor de validação e o serviço de aprovação prontos, mas sem porta de
entrada: nenhuma autenticação, um único controller (upload), e `DocumentApprovalService`/
`DocumentService.transition` a ler documentos só pelo id, sem filtrar por organização. O
código já deixava o terreno preparado a propósito — `CurrentUserProvider` como ponto de
extensão, `DevBootstrap` a dizer no seu próprio Javadoc "a etapa 06 apaga esta classe
inteira", `DomainException` pronta para tradução RFC 7807 centralizada,
`ExtractedField.writtenByHuman`/`correctTo` e `DocumentEvent.fieldCorrected` à espera de
um chamador. Esta sessão ligou os fios: JWT, papéis, isolamento por organização sem
exceção, os endpoints de documentos, e uma revisão de segurança que apanhou uma lacuna
real antes de fechar a etapa.

## O que ficou feito

### Autenticação — `com.docgrid.auth`

- **Access token JWT** (`JwtService`, novo) — HS256, claims `sub`/`org`/`role`, vida curta
  (15 min por omissão, `docgrid.jwt.access-token-ttl`). Stateless, nunca guardado.
- **Refresh token opaco** (`RefreshToken`, `RefreshTokenRepository`, `RefreshTokenService`,
  novos; migração `V7__refresh_tokens.sql`) — 256 bits aleatórios, só o hash SHA-256 chega
  à BD. `rotate(...)` consome o apresentado e emite outro na mesma chamada; um token já
  revogado que volte a ser apresentado revoga **toda a sessão** desse utilizador
  (`InvalidRefreshTokenException`).
- **`AuthService`/`AuthController`** (novos) — `POST /api/auth/register` (cria organização +
  admin numa só chamada), `login`, `refresh`, `logout`. Únicos endpoints públicos.
- **`SecurityConfig`** (novo, `@EnableMethodSecurity`) — sessão sem estado, filtro JWT
  (`JwtAuthenticationFilter`, construído diretamente pelo `SecurityConfig` e não como
  `@Component` — ver Armadilhas), `authorizeHttpRequests` só decide o que é público, o
  resto é `@PreAuthorize` por endpoint. Falhas de autenticação (sem token, token inválido)
  escrevem RFC 7807 diretamente no filtro, antes do `DispatcherServlet`.
- **`AuthenticatedCurrentUserProvider`** (novo) — lê `CurrentUserProvider` das claims do
  token validado. `DevBootstrap` **apagado** por completo, como o seu Javadoc previa;
  `CurrentUserProvider` ganhou `currentRole()`.

### Documentos — `com.docgrid.document`

- **`DocumentController`** (novo) — `GET /api/documents` (filtro estado/fornecedor/período,
  paginado por offset), `/review-queue`, `/approval-queue`, `GET /{id}`,
  `PATCH /{id}/fields/{fieldName}`, `POST /{id}/approve`, `POST /{id}/reject`. Nenhum
  endpoint devolve entidade JPA — `DocumentResponseMapper` traduz sempre para DTO
  (`com.docgrid.document.dto`).
- **`FieldCorrectionService`** (novo) — corrige um campo, grava `HUMAN` + valor anterior
  (`DocumentEvent.fieldCorrected`, já existia desde a etapa 05), atualiza a projeção
  (`Document.projectInvoiceFields`) e revalida. **Nunca** transita `NEEDS_REVIEW` de volta
  a `EXTRACTED` sozinho — essa transição não existe no ciclo de vida; só transita quando a
  transição de facto está no `DocumentStatus` (tipicamente `EXTRACTED → NEEDS_REVIEW`).
- **`DocumentValidationContextFactory`** (novo, extraído de `DocumentProcessor`) — monta o
  `ValidationContext`, reutilizado pela extração inicial e pela correção manual.
  `currentFieldConfidences(document)` trata um campo `HUMAN` como confiança plena (1.0) —
  decisão desta etapa, ver tabela abaixo.
- **`DocumentApprovalService.approve`** e **`DocumentService.transition`** passam a filtrar
  por organização (`findByIdAndOrganizationId`); `approve` ganha a verificação de gestor
  (ver Decisões).
- **`DocumentRejectionService`** (novo) — delega em `DocumentService.transition` para
  `REJECTED`.
- **`DocumentUploadService.fileUrl`** corrigido: lia por id sem filtro de organização,
  mesma brecha dos outros dois serviços.

### Erros e documentação

- **`GlobalExceptionHandler`** (novo, `com.docgrid.shared`) — único `@RestControllerAdvice`;
  `DomainException` ganhou `HttpStatus status()`, cada subclasse diz o seu próprio.
  Substitui o `DocumentExceptionHandler` pontual da etapa 02 (apagado).
- **`OpenApiConfig`** (novo) — esquema `bearerAuth`, aplicado por omissão.
  `/swagger-ui.html` navegável sem token; `/api/admin/dlq` fechado a `ADMIN`.

### Portas novas

- **`com.docgrid.validation.ValidationResultProvider`** (novo, público) — resultados de
  validação fora do pacote, para o detalhe do documento. Mesmo padrão de
  `ApprovalThresholdProvider` (etapa 05).

### Correção pós-revisão de segurança

O `/security-review` obrigatório desta etapa encontrou uma lacuna real: `FINANCE` podia
corrigir `TOTAL_AMOUNT` (endpoint que já lhe era permitido) para um valor abaixo do limite
da organização, e aprovar a seguir sozinho — sem gestor nenhum a validar a correção.
`DocumentApprovalService.approve` passou a exigir `MANAGER`/`ADMIN` também quando qualquer
campo de montante (`NET_AMOUNT`/`VAT_AMOUNT`/`VAT_RATE`/`TOTAL_AMOUNT`) tem origem `HUMAN`,
independentemente do valor corrente (commit `c8977b8`).

### ADR

- `docs/adr/0011-autenticacao-jwt-e-isolamento-por-organizacao.md` — par de tokens, rotação
  e deteção de reutilização, paginação por offset, aprovação por verificação em vez de
  estado novo (e a correção da lacuna acima), isolamento por organização transversal.

## Decisões tomadas

| Decisão | Alternativa posta de lado | Porquê |
|---|---|---|
| **Refresh token opaco, hash SHA-256 em BD, com rotação e deteção de reutilização** | Refresh também como JWT stateless | Revogar (logout, comprometimento) precisaria de estado de qualquer forma; o opaco é mais simples e mais barato de revogar |
| **Paginação por offset** (`Pageable`) | Cursor (keyset) | Volume esperado (100–300 documentos/mês por organização) nunca justifica a complexidade de um cursor opaco |
| **Aprovação de gestor por verificação no momento de aprovar**, sem estado novo em `DocumentStatus` | Estado `PENDING_MANAGER_APPROVAL` | Evita reabrir a máquina de 8 estados/11 transições já fechada e testada, por um resultado observável equivalente |
| **Verificação de gestor também cobre montante corrigido à mão**, não só o valor corrente | Só comparar `totalAmount` corrente com o limite | Achado da revisão de segurança: sem isto, `FINANCE` contornava o limite corrigindo o total antes de aprovar |
| **Campo `HUMAN` conta como confiança plena (1.0)** na revalidação | Tratar como "campo ausente" (como a extração inicial trata um campo nunca lido) | A origem `HUMAN` já diz que uma pessoa confirmou o valor; tratá-lo como ausente faria `MIN_CONFIDENCE` falhar para sempre depois de qualquer correção |
| **`JwtAuthenticationFilter` não é `@Component`** | Registá-lo como bean, injetado no `SecurityConfig` | Um `Filter` registado como bean é também autorregistado pelo Spring Boot no servlet container — corria duas vezes. `SecurityConfig` constrói-o diretamente |
| **`DevBootstrap` apagado por completo** | Manter para o perfil `local` continuar a semear sozinho | O seu próprio Javadoc já dizia "a etapa 06 apaga esta classe inteira"; local passa a usar `POST /api/auth/register` |

ADRs escritos: `docs/adr/0011-autenticacao-jwt-e-isolamento-por-organizacao.md`

## Como verificar

### Testes e formatação

Precisa do Docker Desktop (Testcontainers: Postgres + LocalStack).

```bash
npm run lint
# BUILD SUCCESS

npm test
# Tests run: 269, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS
```

De 230 para 269: **39 testes novos** — `JwtServiceTest` (unitário puro), `RefreshTokenServiceTest`
(`@RepositoryTest`), `AuthServiceTest`/`AuthControllerTest` (registo/login/refresh/logout,
RFC 7807), `DocumentControllerTest` (isolamento por organização e por `EMPLOYEE`, 403 por
papel), `FieldCorrectionServiceTest`, `DlqAdminAuthorizationTest`, `SwaggerUiTest`, mais
casos novos em `DocumentApprovalServiceTest`/`DocumentServiceTest` para o isolamento e o
limite de aprovação.

### Fluxo completo por `curl`

```bash
npm run up
# noutro terminal:
RESP=$(curl -s -XPOST localhost:8080/api/auth/register -H 'content-type: application/json' -d '{
  "organizationName": "Padaria do Bairro, Lda.",
  "organizationTaxId": "501442889",
  "adminEmail": "ana@padaria.pt",
  "adminPassword": "uma-password-forte",
  "adminFullName": "Ana Ribeiro"
}')
TOKEN=$(echo "$RESP" | grep -o '"accessToken":"[^"]*"' | cut -d'"' -f4)

curl -s localhost:8080/api/documents -H "Authorization: Bearer $TOKEN"
#  -> {"items":[],"page":0,"size":20,"totalElements":0,"totalPages":0}

curl -s -o /dev/null -w '%{http_code}\n' localhost:8080/swagger-ui.html
#  -> 200, sem token

curl -s -o /dev/null -w '%{http_code}\n' localhost:8080/api/documents
#  -> 401, sem token
```

**Não executado nesta sessão** com o servidor real (`npm run up`) — verificado por
`AuthControllerTest`, `DocumentControllerTest` e `SwaggerUiTest`, que exercitam a mesma
cadeia (MockMvc + Spring Security real + Postgres real via Testcontainers), não pela
aplicação a correr fora dos testes.

## O que ficou por fazer

Nada em falta bloqueia a etapa 07. Fora de âmbito por decisão:

- **Frontend** — etapa 07.
- **Dashboard e exportação** — etapa 09.
- **Verificar se o utilizador continua ativo a cada pedido** — o access token de 15 min
  limita a janela de desfasamento; revisitar só se um dia isto importar.
- **Gestão de utilizadores** (convidar, mudar papel, desativar) — não pedida pelo briefing
  desta etapa; hoje só existe `register` (cria organização + admin).

## Armadilhas para a próxima sessão

1. **`JwtAuthenticationFilter` não é `@Component`.** Se um dia precisares de o injetar
   noutro sítio, resiste à tentação de lhe pôr `@Component` — um `Filter` registado como
   bean Spring é também autorregistado pelo Spring Boot como filtro do servlet container
   inteiro, e corre duas vezes por pedido. `SecurityConfig.filterChain(...)` constrói-o
   diretamente com `new JwtAuthenticationFilter(jwtService)`.
2. **`DocumentRepository.search(...)` usa `coalesce`, não `:param is null or ...`, para os
   filtros de período.** O Postgres (Hibernate 6.6 + PgJDBC) não consegue inferir o tipo de
   um parâmetro `Instant` comparado só em `is null` — tenta `bytea` e a comparação com
   `timestamptz` rebenta em runtime (`could not determine data type of parameter`). Se
   acrescentares outro filtro `Instant`/`LocalDate` opcional a uma `@Query`, usa o mesmo
   padrão `coalesce(:param, coluna)`.
3. **`FixedCurrentUserProvider` (dentro de `DemoIdentityConfiguration`, testes) não pode
   ser `final`.** O Spring precisa de gerar um subtipo CGLIB dele (por causa do
   `@Transactional` em `run(...)` e do `@TestConfiguration` que o expõe); uma classe
   `final` faz `BeanCreationException` em cascata em todos os testes que a importam.
4. **`ExtractedField.readByMachine`/`writtenByHuman` já se registam sozinhos em
   `document.addExtractedField(...)`.** Chamar `fields.save(...)` a seguir NUM DOCUMENTO
   JÁ PERSISTIDO (via `documents.save(document)`, que faz `merge()` porque o id nunca é
   nulo) associa uma segunda instância gerida com o mesmo id à sessão e rebenta com
   `DuplicateKeyException` ("a different object with the same identifier"). Sobre um
   documento ainda não persistido (fixture construída de raiz) é inofensivo. Apanhado em
   `FieldCorrectionServiceTest`.
5. **Testes `@SpringBootTest` que mutam o estado de um documento sem `@Transactional` na
   classe têm de reatribuir o retorno de `documents.save(...)`** — `document.transitionTo(...)`
   numa entidade obtida de `documents.findById(...)` sem uma transação a envolver todo o
   método não persiste nada sozinha (o `open-in-view: false` do projeto fecha o
   `EntityManager` no fim de cada chamada ao repositório). `DocumentApprovalServiceTest`
   evita isto construindo o documento sempre de raiz, nunca refazendo `findById` a meio.
6. **Fixtures do stub com data fixa continuam a apodrecer** (armadilha já registada na
   etapa 05, ainda válida) — `clean-invoice.json` e afins têm `issueDate: 2026-08-20`.

## Ficheiros centrais desta etapa

- `backend/src/main/java/com/docgrid/auth/SecurityConfig.java` — o que é público, o filtro
  JWT, o formato de erro antes do `DispatcherServlet`.
- `backend/src/main/java/com/docgrid/auth/RefreshTokenService.java` — rotação e deteção de
  reutilização.
- `backend/src/main/java/com/docgrid/document/DocumentController.java` — os sete
  endpoints, o filtro `EMPLOYEE`.
- `backend/src/main/java/com/docgrid/document/DocumentApprovalService.java` — o limite de
  aprovação e a correção pós-revisão de segurança.
- `backend/src/main/java/com/docgrid/document/FieldCorrectionService.java` — correção
  manual e revalidação sem saltar o ciclo de vida.
- `backend/src/main/java/com/docgrid/shared/GlobalExceptionHandler.java` — RFC 7807
  centralizado.
- `docs/adr/0011-autenticacao-jwt-e-isolamento-por-organizacao.md`

## Commits

```
c8977b8 fix(document): exige gestor para aprovar um documento com montante corrigido à mão
6e42183 test(shared): Swagger UI navegável e OpenAPI lista os endpoints novos
f440c43 docs(adr): autenticação JWT e isolamento por organização
e2f3e11 feat(shared): Swagger UI com esquema de autenticação Bearer
5ba9f98 fix(pipeline): fecha /api/admin/dlq a ADMIN
331c82e feat(document): endpoints de listagem, detalhe, filas, aprovação e rejeição
f3e46f5 feat(document): rejeitar um documento
045798b feat(document): corrigir campo à mão, com origem HUMAN e revalidação
9e2becf feat(validation): porta pública para os resultados de validação
4a071c6 fix(document): isolamento por organização em todas as escritas
ac03cc7 refactor(document): extrai a fábrica do contexto de validação
53abfb4 feat(auth): autenticação por token substitui o utilizador de demonstração
d7d3e09 feat(auth): registo, login, refresh e logout
659b8d6 feat(auth): emissão e validação de access tokens JWT
e176926 feat(auth): tabela e serviço de refresh tokens com rotação
766754a fix(shared): RFC 7807 centralizado a partir do estado de cada DomainException
259d6cb chore(deps): adiciona Spring Security, JWT e springdoc-openapi
```
