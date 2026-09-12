# Handoff — Etapa 07: Frontend base

**Data:** 2026-09-12 · **Sessão:** #8 (+ revisão na #9) · **Estado:** completa

## Contexto de arranque

A etapa 06 deixou a API REST e a autenticação JWT prontas e testadas, mas sem nada que as
consumisse — `frontend/` era uma pasta vazia com um `.gitkeep`. Esta sessão criou a
aplicação React de raiz: login, listagem de documentos, fila de revisão e upload direto
para o S3, seguindo a ADR-0001 (Mantine + TanStack Table, sem Tailwind).

## O que ficou feito

### Scaffold e infraestrutura de projeto — `frontend/`

- **Vite + React 18 + TypeScript estrito** (`tsconfig.app.json`: `strict`,
  `noUncheckedIndexedAccess`, `noUnusedLocals`) + ESLint com `@typescript-eslint/no-explicit-any`
  como erro. Projeto Node à parte, com o seu próprio `package.json` — os comandos fixos da
  raiz (`npm run up`/`test`/`lint`) continuam a ser só do backend, por decisão desta sessão
  (ver tabela de decisões).
- **Proxy do Vite** (`vite.config.ts`): `/api` → `http://localhost:8080` em desenvolvimento,
  para não precisar de CORS no Spring Security.
- **Vitest + Testing Library + MSW** para testes, com `src/test/setup.ts` a polyfillar o que
  o jsdom não tem (`matchMedia`, `ResizeObserver`, `scrollIntoView`).

### Cliente de API e autenticação — `src/api/`, `src/auth/`

- **`api/types.ts`** — DTOs copiados à mão dos `.java` do backend (`docs/03-CONVENTIONS.md`
  aceita explicitamente "gerados ou copiados de uma única fonte"; com ~10 DTOs, gerar a
  partir do OpenAPI trazia uma dependência e um script só para isto).
- **`api/client.ts`** — `apiFetch` guarda o **access token só em memória** (nunca em
  `localStorage`) e o **refresh token em `localStorage`** (o backend devolve-o no corpo
  JSON, não em cookie `httpOnly`, por isso persistir algo é inevitável para sobreviver a um
  F5). Num `401`, tenta uma única renovação com mutex (`refreshInFlight`) e repete o pedido
  original; um temporizador (`scheduleProactiveRefresh`) renova ~60 s antes de
  `accessTokenExpiresAt`, para o polling em segundo plano não falhar visivelmente.
  `initializeSession()` tenta renovar a sessão no arranque se houver um refresh token de
  uma visita anterior — sem isto, um F5 mandaria sempre para `/login` mesmo com sessão
  válida.
- **`auth/AuthContext.tsx`**, **`RequireAuth.tsx`**, **`RequireRole.tsx`** — sessão decodificada
  das claims do JWT (`sub`/`org`/`role`, sem nome nem email — esses vêm do que o utilizador
  escreveu no login e ficam em `localStorage` só para reexibição no cabeçalho).
  `RequireRole` bloqueia antes de qualquer pedido à API — cumpre o critério de aceitação
  "`EMPLOYEE` não chega à fila de revisão nem por URL direto".

### Ecrãs — `src/features/`, `src/layout/`

- **`features/auth/LoginPage.tsx`** — único ecrã de autenticação. Sem registo: contas
  continuam a criar-se por `POST /api/auth/register` (curl/Swagger), como já era.
- **`features/documents/`** — `DocumentsListPage`, `DocumentsTable` (TanStack Table headless
  - Mantine `Table`), `DocumentFilters` (estado, NIF, período — inputs `type="date"`
  nativos, não `@mantine/dates`, que não estava na lista de dependências da ADR-0001),
  `useDocuments` com `refetchInterval` dinâmico: só faz polling (4 s) enquanto existir
  algum documento em `UPLOADED`/`PROCESSING` na página visível.
- **`features/review/ReviewQueuePage.tsx`** — página própria, `FINANCE`/`MANAGER`/`ADMIN`,
  reutiliza `DocumentsTable` sobre `GET /api/documents/review-queue`. Só visibilidade;
  aprovar/rejeitar/corrigir é etapa 08. Polling constante a 15 s
  (`features/review/reviewQueuePolling.ts`), partilhado com o indicador da navegação.
- **`features/upload/`** — `Dropzone` do Mantine para múltiplos ficheiros;
  `useDocumentUpload` pede `upload-url` e depois faz `PUT` para o S3 via `XMLHttpRequest`
  (não `fetch`, para ter `xhr.upload.onprogress`), com o `XhrFactory` injetável para os
  testes. Validação de tipo/tamanho no cliente espelha `docgrid.upload` (não há endpoint
  para a consultar); a falha de um ficheiro não trava os outros. Um ficheiro recusado pelo
  `Dropzone` entra na mesma lista com o motivo (`onReject`), e cada lote termina com um
  aviso do `@mantine/notifications` e a invalidação da query `["documents"]`.
- **`layout/AppShellLayout.tsx`** — `AppShell` do Mantine, navbar colapsável em telemóvel
  (`Burger`), cabeçalho com email/papel e logout, `ReviewQueueBadge` visível só aos papéis
  que podem chegar à fila.

### Infraestrutura — fora de `frontend/`

- **`docker/localstack/init/ready.d/docgrid-resources.sh`** — `put-bucket-cors` no bucket
  local. Descoberto a meio da etapa: até agora o upload só tinha sido testado por `curl`
  (etapas 02/05/06), que não passa por CORS; um browser real falha o preflight `OPTIONS`
  sem isto. Origem autorizada: `http://localhost:5173` (Vite dev server). A etapa 11 trata
  da política de CORS em AWS real.
- **`README.md`** — secção "Frontend" com os comandos (`cd frontend && npm install && npm run dev`).

## Decisões tomadas

| Decisão | Alternativa posta de lado | Porquê |
| --- | --- | --- |
| **Polling curto (4 s)**, dinâmico, só enquanto há documento não terminal | WebSocket/SSE | Exigiria endpoint novo no backend só para poupar um punhado de pedidos curtos (extração demora 5–30 s, ADR-0008); honesto e simples, como o briefing pedia |
| **CORS via proxy do Vite** em desenvolvimento | CORS no Spring Security | Mantém a etapa dentro do frontend; zero mudança de comportamento do backend |
| **Tipos copiados à mão** num único ficheiro | Gerar com `openapi-typescript` | ~10 DTOs não justificam uma dependência, um script e exigir o backend de pé para atualizar tipos |
| **Access token só em memória; refresh token em `localStorage`** | Ambos em `localStorage` | Reduz a janela de exposição a XSS para o token de vida curta; o refresh já tem rotação e deteção de reutilização do lado do servidor (etapa 06) |
| **`react-router-dom`** para rotas | Rotas geridas à mão | Padrão do ecossistema; sem alternativa razoável sem reinventar um router |
| **Sem ecrã de registo** | Ecrã de registo no frontend | Não pedido pelo briefing; criar organização é um evento raro, não uma jornada recorrente |
| **Fila de revisão como página própria**, sem ações | Só um contador no cabeçalho | O critério "`EMPLOYEE` não chega à fila nem por URL direto" implica uma rota concreta para o guarda de rota proteger |
| **Inputs de data nativos (`type="date"`)** nos filtros | `@mantine/dates` | Não estava na lista de dependências por etapa da ADR-0001; evita uma dependência nova para um caso simples |
| **CORS no bucket local do LocalStack** (fora de `frontend/`) | Deixar para a etapa 11 | Bloqueava o próprio critério de aceitação desta etapa (upload real pelo browser); não é uma funcionalidade nova, é um pré-requisito técnico descoberto a meio |
| **Comandos do frontend não entram nos scripts fixos da raiz** (`npm run up`/`test`/`lint`) | Adicionar `npm run dev:frontend` etc. à raiz | `AGENTS.md` fixa esses comandos para o backend; o frontend é um projeto Node à parte, documentado no README, sem alterar o significado dos comandos existentes |

Nenhum ADR novo escrito — as decisões desta etapa já estavam justificadas na ADR-0001
(biblioteca de UI) ou são de âmbito local sem alternativa realmente defensável a registar.

## Como verificar

```bash
cd frontend
npm install
npx tsc -b --noEmit      # sem erros
npx eslint .              # 0 erros, 2 avisos de react-refresh (aceitáveis)
npx vitest run
# Test Files  10 passed (10)
#      Tests  25 passed (25)
#   Duration  ~4 s
```

Fluxo manual, com o backend a correr (`npm run up` na raiz, noutro terminal):

```bash
cd frontend && npm run dev
# http://localhost:5173
```

1. Cria uma conta por `POST /api/auth/register` (ver exemplo no handoff da etapa 06) e
   inicia sessão em `/login` com esse email/password.
2. `/upload`: arrasta 5 ficheiros PDF/JPEG/PNG — 5 barras de progresso independentes.
3. `/documents`: o documento aparece e muda de estado sozinho (polling) sem recarregar.
4. Inicia sessão como `EMPLOYEE` e tenta `/review-queue` diretamente pelo URL — "Sem
   permissão", sem pedido à API.
5. DevTools em viewport de telemóvel — navbar colapsa num `Burger`.

**Corrido de facto num browser na sessão #9**, com o stack todo de pé: os passos 1 a 4
passam. O passo 5 (telemóvel) continua **por verificar** — ver "O que ficou por fazer".

## O que ficou por fazer

Nada bloqueia a etapa 08. Fora de âmbito por decisão:

- **Ecrã de revisão detalhada, correção de campos, aprovar/rejeitar** — etapa 08.
- **Dashboard e exportação** — etapa 09.
- **Ecrã de registo de organização, gestão de utilizadores** — não pedido pelo briefing.
- **CORS de produção no Terraform** — etapa 11, com a origem real do frontend implantado.
- **Verificação em ecrã de telemóvel** — o único critério de aceitação que continua por
  confirmar. Na sessão #9 a extensão de browser não conseguiu reduzir o viewport (ficava
  nos 1536 px) e não há emulação de dispositivo disponível. Faz-se à mão nas DevTools.
- **Testes do frontend fora de `npm test` da raiz** — decisão consciente (ver Decisões),
  mas a etapa 11 tem de os pôr no CI, ou passam a correr só por hábito de quem lembrar.

## Armadilhas para a próxima sessão

1. **Um `Combobox` do Mantine com o dropdown montado envenena o ficheiro de teste inteiro.**
   O `Combobox.Dropdown` fica montado com a lista fechada (`keepMounted` por omissão) e essa
   árvore nunca assenta em jsdom: bastava um `Select` renderizado uma vez para os testes
   seguintes do mesmo ficheiro passarem a 5 s cada. `comboboxProps={{ keepMounted: false }}`
   resolve — 12 784 ms → 38 ms num ficheiro de dois testes, e a suite de 177 s para 4,2 s.
   Se aparecer um `Select`, `Autocomplete` ou `MultiSelect` novo, passa-lhe a mesma opção.

   *(O handoff original atribuía isto ao floating-ui sem `requestAnimationFrame`/
   `IntersectionObserver` nativos. Estava errado, e os polyfills que daí vieram não faziam
   nada: o jsdom do vitest já traz rAF, e removê-los não muda um milissegundo. Um `Popover`
   fechado renderiza em 28 ms — o problema era só do `Combobox`.)*
2. **`client.ts` guarda o access token em estado de módulo, partilhado por todos os testes
   do mesmo ficheiro.** `src/test/setup.ts` chama `clearSession()` num `afterEach` global —
   sem isto, uma sessão criada por um teste (`setSession`) vaza para o seguinte e mascara
   redirecionamentos de "sem sessão". Apanhado em `App.test.tsx`.
3. **`getByLabelText("Email")` falha em inputs `required` do Mantine** — o `*` do indicador
   de campo obrigatório entra no texto acessível do label ("Email *"), quebrando a
   correspondência exata. Usa `{ exact: false }` ou uma regex nas queries de label sobre
   campos obrigatórios.
4. **`@mantine/dates` não está instalado** — de propósito (ver Decisões). Se uma etapa
   futura precisar de um date picker a sério (não só `type="date"`), é aí que entra.
5. **`frontend/vite.config.ts` faz proxy de `/api` para `localhost:8080` fixo** — se a porta
   do backend mudar via `SERVER_PORT`, o proxy tem de acompanhar.

## Ficheiros centrais desta etapa

- `frontend/src/api/client.ts` — `apiFetch`, renovação de sessão, mutex de refresh.
- `frontend/src/auth/AuthContext.tsx` — sessão decodificada do JWT, `RequireAuth`/`RequireRole`.
- `frontend/src/features/documents/useDocuments.ts` — polling dinâmico.
- `frontend/src/features/upload/useDocumentUpload.ts` — upload por XHR com progresso.
- `frontend/src/test/setup.ts` — polyfills de jsdom, limpeza de sessão entre testes.
- `frontend/src/features/review/reviewQueuePolling.ts` — intervalo da fila, num sítio só.
- `docker/localstack/init/ready.d/docgrid-resources.sh` — CORS do bucket local.

## Revisão da etapa (sessão #9)

A etapa foi revista depois de fechada. O que saiu de lá, por ordem de importância:

| Achado | Correção |
| --- | --- |
| Suite de testes a 177 s, com diagnóstico errado no handoff | `keepMounted: false` no `Select`; caem 3 polyfills inúteis e os tempos-limite inflacionados. 4,2 s |
| Ficheiros recusados pelo `Dropzone` desapareciam sem deixar rasto (sem `onReject`) | Entram na lista com o motivo; o `validateFile`, que a interface nunca alcançava, volta a servir |
| `@mantine/notifications` montado e nunca usado, contra a ADR-0001 | Aviso por lote no fim do upload, e a lista de documentos passa a ser invalidada |
| Sem teste do `UploadPage` — o critério das 5 barras só estava coberto no hook | `UploadPage.test.tsx` |
| `BigDecimal` tipado como `string` na "única fonte de tipos" | Montantes e confiança passam a `number`; correspondência Java → JSON documentada |
| `issueDate` formatada via `new Date()` — meia-noite UTC | Data de calendário construída no fuso local |
| Fila de revisão em polling de 4 s para sempre | 15 s, na mesma constante do indicador |
| `/login` acessível com sessão ativa; cartão de 360 px fixo | Redireciona; largura responsiva |

**Verificado num browser real** (Postgres + LocalStack + backend + Vite): preflight de CORS
do LocalStack devolve `Access-Control-Allow-Origin: http://localhost:5173` — a política do
bucket é honrada e **não** é preciso `EXTRA_CORS_ALLOWED_ORIGINS`; 5 PDFs dão 5 barras
independentes e chegam ao S3; um `.docx` aparece recusado com o motivo; a lista mostra
`123,00 EUR` e `20/08/2026`; logout e redirecionamento de `/login` funcionam; um `EMPLOYEE`
em `/review-queue` vê "Sem permissão" e não tem sequer o link na navegação.

Não confirmado: **ecrã de telemóvel** (ver "O que ficou por fazer").

## Commits

Da revisão (sessão #9):

```
5c9199a refactor(frontend): remove hasStoredSession, que ninguém chamava
9f39dd8 fix(frontend): ecrã de login com sessão ativa e em ecrã estreito
d39ecf7 fix(frontend): polling da fila de revisão ao ritmo de quem a usa
d1481c3 fix(frontend): data de emissão não muda de dia com o fuso horário
d15be4a fix(frontend): montantes e confiança são números, não strings
d85df02 fix(frontend): o upload deixa de engolir ficheiros em silêncio
9a16419 test(frontend): corrige a lentidão dos testes com Select
```

Da implementação (sessão #8):

```
4e1411a docs: secção de frontend no README
4abaa0a fix(pipeline): CORS no bucket local para upload direto do browser
23c7617 feat(frontend): liga as rotas e arranca a aplicação
57e55fb feat(frontend): upload de documentos com progresso por ficheiro
550e916 feat(frontend): fila de revisão
9339d08 feat(frontend): lista de documentos com filtros, paginação e atualização automática
9e78e9a feat(frontend): ecrã de login
0a0ff4e feat(frontend): layout com navegação lateral e indicador de fila de revisão
de336af feat(frontend): cliente de API, tipos e autenticação com renovação automática
9d68890 chore(frontend): scaffold do projeto Vite + React + TypeScript + Mantine
```
