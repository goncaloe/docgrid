# Plano — Etapa 12: Vitrine

**Data:** 2026-09-15 · **Planeado com:** Opus 5 · **Estado:** aprovado

> Este plano vai ser executado noutra sessão, sem memória da conversa que o produziu.
> O que não estiver aqui, desaparece. Caminhos exatos, não descrições.
>
> E não é um documento morto: quem implementa escreve nele quando ele deixar de bater certo
> com o disco — ver "Desvios durante a execução", no fim.

## Contexto

O código está feito e a etapa 11 fechou a infraestrutura (Terraform escrito e validado,
nunca aplicado — sem conta AWS). O que falta não é funcionalidade: é o repositório
convencer alguém em três minutos. Hoje, quem clona encontra um README que descreve a
stack mas não mostra o produto, tabelas vazias porque não há dados, nenhuma licença, e
dois ADRs com o título em galego.

O nó técnico desta etapa está identificado: `StubExtractor`
(`backend/src/main/java/com/docgrid/extraction/StubExtractor.java`) devolve **sempre a
mesma fixture**, e não há um único PDF no repositório. Um seed ingénuo produziria 60
documentos idênticos, com o mesmo hash — e o ecrã de revisão, que é o que se filma,
apareceria sem documento do lado esquerdo.

## O que se vai construir

1. **`npm run seed`** — um runner Java sob o perfil `demo` que gera 60 faturas sintéticas
   coerentes (NIF com dígito de controlo válido, IVA a 6/13/23 %, datas nos últimos 6
   meses), desenha um PDF por fatura, sobe-o ao S3 e deixa o **pipeline real** processá-lo;
   depois corrige, aprova, rejeita e fecha dois períodos pelos serviços de domínio.
2. **README de vitrine**, pela ordem de `docs/06-DEMO-CHECKLIST.md`, com dois diagramas
   Mermaid (arquitetura e ciclo de vida do documento).
3. **`LICENSE` (MIT) e `CONTRIBUTING.md`**, e um **índice dos ADRs** com o que cada um decide.
4. **Coerência linguística**: galeguismos fora dos ADRs 0017-0019, do README, de `docs/02`
   e `docs/04`; os dois ADRs renomeados; `infra/terraform/README.md` traduzido.
5. **Guiões de media** (`docs/media/`): o percurso exato a filmar para o GIF e o guião
   cronometrado do vídeo de 2 minutos — a gravação é do autor.

## Pré-requisitos verificados

- Pipeline completo no disco: `DocumentProcessor.process(...)`, `StorageService.put(...)`,
  `DocumentUploadService.authorizeUpload(...)`, `DocumentApprovalService.approve(...)`,
  `DocumentRejectionService.reject(...)`, `FieldCorrectionService.correct(...)` e
  `ExportService.create(...)` são todos **públicos** — o seed entra pelo domínio, sem HTTP.
- Padrão de identidade sem pedido HTTP já existe:
  `backend/src/test/java/com/docgrid/auth/DemoIdentityConfiguration.java` (é o molde a copiar).
- O LocalStack já liga S3 → SQS (`docker/localstack/init/ready.d/docgrid-resources.sh`),
  e o perfil `local` inclui o grupo `worker` (`application.yml`) — um `put` no bucket é
  processado pelo worker do mesmo processo.
- Dígito de controlo do NIF em `backend/src/main/java/com/docgrid/validation/TaxIdRule.java`
  (`hasValidCheckDigit`) — a fórmula a replicar no gerador.
- Frontend completo: `frontend/src/features/review/` tem `BoundingBoxOverlay`,
  `DuplicateCard`, `DocumentPreview`; `dashboard/`, `exports/` e `documents/` existem.
- Sem TODOs, sem código morto, `.gitignore` limpo (verificado com `git grep` e `git ls-files`).

## Decisões tomadas

| Decisão | Alternativa posta de lado | Porquê |
|---|---|---|
| Seed como runner Java no perfil `demo`, pelos serviços de domínio | Script Node contra a API REST; `.sql` aplicado com `psql` | O Node não resolve a variação da extração nem cria utilizadores com papéis (não há endpoint); o SQL contorna o domínio, apodrece à primeira migração e não põe ficheiros no S3 — o ecrã de revisão ficaria sem documento |
| O seed sobe ao S3 e **espera pelo worker** | Chamar `DocumentProcessor.process(...)` diretamente | A notificação S3→SQS dispara de qualquer forma; com duas vias havia corrida entre o seed e o worker. Uma só via, e o dado semeado é indistinguível de um documento real |
| PDF escrito à mão (PDF 1.4, sem compressão), ~60 linhas | Dependência nova no `pom.xml` (PDFBox, OpenPDF) | Uma dependência de 5 MB só para dados de demonstração; e o gerador próprio põe cada campo **na coordenada da sua geometria**, que é o que faz a bounding box acertar no ecrã de revisão |
| `DemoExtractor` lê um marcador `%DocGridDemo: <slug>` no PDF | Estender `docgrid.extraction.stub-fixture` a um catálogo | Não se toca em código de produção por causa da demonstração; o marcador é um comentário PDF legal, que os leitores ignoram |
| Pacote `com.docgrid.demo` em `src/main`, ativo só com `@Profile("demo")` | Pôr o seed em `src/test`; módulo Maven à parte | `spring-boot:run` não vê `src/test`; um módulo novo é desproporcionado. A imagem de produção nunca ativa o perfil |
| MIT | Apache-2.0; sem licença | Três parágrafos que toda a gente reconhece |
| Renomear os ADRs 0017 e 0019 e atualizar as 7 referências | Corrigir só o corpo | A lista de ADRs lê-se inteira e é dela que vem metade do sinal técnico |
| GIF e vídeo gravados pelo autor, com guião escrito nesta etapa | Gravar o GIF por automação do browser | Decisão do autor: mais controlo sobre ritmo e cursor |

ADRs a escrever: `docs/adr/0021-postgres-como-armazenamento-unico.md` (retroativo — a decisão
existe desde a etapa 01 e nunca foi escrita; o README precisa dela no parágrafo "porquê
Postgres e não DynamoDB").

## Riscos e pontos de paragem

- **O PDF gerado à mão não renderiza no `pdf.js` do frontend** → verifica-o cedo (passo 4,
  abrindo um PDF gerado no browser). Se não renderizar, **para e pergunta** antes de
  adicionar uma dependência ao `pom.xml` — é uma alteração de stack, e a stack está fixa
  no `AGENTS.md`.
- **O seed demora mais de 2 minutos** → reduz o volume ou a espera por documento. **Não
  paralelizes** o envio para a fila: o objetivo é histórico realista, não um teste de carga.
- **Um `DemoExtractor` com `@Primary` não chega e o contexto acusa dois beans
  `DocumentExtractor`** → **para e pergunta**. Não alteres o `@Profile` do `StubExtractor`:
  é código de produção e três etapas dependem dele.
- **O seed corrido duas vezes duplica tudo** → tem de abortar com mensagem clara quando a
  organização de demonstração já existir ("corre `npm run down` primeiro").
- **A verificação em máquina limpa falha porque a porta 8080 está ocupada** (armadilha
  conhecida do handoff 11) → é da máquina, não do repositório: regista e segue.
- **Aparece uma falha funcional ao preparar a demonstração** → o briefing é explícito:
  documenta como limitação conhecida no README e segue. Só para se impedir o seed de correr.
- **Não apagues ADRs** para chegar aos "5 a 8" do briefing: são 20 e todos legítimos; o que
  a etapa pede de facto é que se perceba cada decisão sem ler código — resolve-se com o índice.

## Passos de implementação

Por ordem. Cada passo é uma unidade de commit.

1. **Licença e contribuição** — `LICENSE` (criar), `CONTRIBUTING.md` (criar)
   - MIT, © 2026 Gonçalo Esteves. `CONTRIBUTING.md` curto: como correr, Conventional
     Commits em português, código em inglês, quando se escreve um ADR, onde vivem as regras
     (`AGENTS.md`).
   - Commit: `docs: licença MIT e guia de contribuição`

2. **Coerência linguística** — `git mv` + edições
   - `docs/adr/0017-computacion-e-rede-en-aws.md` → `0017-computacao-e-rede-na-aws.md`;
     `docs/adr/0019-infraestrutura-como-deseno.md` → `0019-infraestrutura-como-desenho.md`.
   - Atualizar as 7 referências em: `AGENTS.md`, `README.md` (2), `docs/02-ARCHITECTURE.md` (2),
     `docs/handoffs/STAGE-11-handoff.md`, `infra/terraform/README.md`.
   - Corpo: "Computación"→"Computação", "aceptado"→"aceite", "Decisión"→"Decisão" nos ADRs
     0017-0019; "imaxe"→"imagem" em `docs/04-ROADMAP.md`; "alineado co"→"alinhado com" e
     "elección da computación"→"escolha da computação" em `AGENTS.md`.
   - Traduzir `infra/terraform/README.md` para português (está inteiramente em inglês,
     contra a regra 5 do `AGENTS.md`).
   - Commit: `docs: português coerente nos ADRs e na infraestrutura`

3. **Catálogo de faturas de demonstração** — `backend/src/main/java/com/docgrid/demo/`
   (criar `package-info.java`, `DemoInvoice.java`, `DemoInvoiceCatalog.java`)
   - `DemoInvoice`: record com `slug`, `InvoiceFields`, `Map<ExtractedFieldName, BigDecimal>`
     de confianças, `Map<ExtractedFieldName, FieldGeometry>` e o caso que representa.
   - `DemoInvoiceCatalog`: gera N faturas **determinísticas** (`new Random(20260915)`),
     com fornecedores portugueses fictícios, NIF válido pela fórmula de `TaxIdRule`,
     `issue_date` espalhada pelos últimos 6 meses, `net/vat/total` coerentes a 6, 13 ou 23 %.
     Casos plantados nas 60: **8** com um campo abaixo de 0,85 (confiança baixa), **3** com
     aritmética errada, **2** com NIF inválido, **2** sem `issue_date` ou sem
     `invoice_number`, e **1 par** com o mesmo NIF + número de fatura (o duplicado que o
     checklist exige poder mostrar-se a acontecer).
   - `package-info.java` explica que o pacote só existe no perfil `demo` e porquê.
   - Testes: `backend/src/test/java/com/docgrid/demo/DemoInvoiceCatalogTest.java` — 60
     entradas, todos os NIFs que se dizem válidos passam no `TaxIdRule`, cada caso plantado
     está presente, duas invocações dão o mesmo resultado (determinismo).
   - Commit: `feat(demo): catálogo de faturas de demonstração`

4. **PDF da fatura** — `backend/src/main/java/com/docgrid/demo/InvoicePdfWriter.java` (criar)
   - Escreve um PDF 1.4 de uma página, sem compressão, com Helvetica: cabeçalho do
     fornecedor, linhas da fatura e os totais. **Cada campo é desenhado na posição derivada
     do polígono da sua `FieldGeometry`** — assim o `BoundingBoxOverlay` do frontend acerta
     no sítio certo, que é o detalhe que faz o GIF impressionar.
   - Segunda linha do ficheiro: `%DocGridDemo: <slug>` (comentário PDF, ignorado pelos leitores).
   - Testes: `backend/src/test/java/com/docgrid/demo/InvoicePdfWriterTest.java` — começa por
     `%PDF-`, contém o marcador, duas faturas diferentes dão bytes diferentes (hashes
     distintos ⇒ a deteção de duplicado binário não dispara em todas).
   - **Verificação manual obrigatória neste passo**: gravar um PDF gerado em disco e abri-lo
     no browser. Se não renderizar, ver "Riscos".
   - Commit: `feat(demo): PDF de fatura gerado sem dependências novas`

5. **Extractor e identidade do perfil `demo`** —
   `backend/src/main/java/com/docgrid/demo/DemoExtractor.java`,
   `DemoCurrentUserProvider.java`, `backend/src/main/java/com/docgrid/auth/DemoUserFactory.java` (criar)
   - `DemoExtractor implements DocumentExtractor`, `@Profile("demo") @Primary`: lê o marcador
     nos primeiros bytes e devolve o `ExtractionResult` da fatura correspondente; sem
     marcador, devolve a primeira do catálogo (comportamento documentado no Javadoc).
   - `DemoCurrentUserProvider implements CurrentUserProvider`, `@Profile("demo") @Primary`:
     identidade mutável que o runner aponta ao utilizador que está a agir em cada momento —
     ver o molde em `backend/src/test/java/com/docgrid/auth/DemoIdentityConfiguration.java`.
   - `DemoUserFactory` vive em `com.docgrid.auth` (`@Profile("demo")`) porque `User` e
     `UserRepository` são package-private: expõe `UUID create(UUID orgId, String email,
     String nome, UserRole papel, String password)` usando o `PasswordEncoder` do contexto.
   - Testes: `backend/src/test/java/com/docgrid/demo/DemoExtractorTest.java` — escrever com
     `InvoicePdfWriter` e voltar a ler devolve a mesma fatura.
   - Commit: `feat(demo): extractor e identidade do perfil demo`

6. **O seed** — `backend/src/main/java/com/docgrid/demo/DemoSeedRunner.java`,
   `DemoProperties.java`, `backend/src/main/resources/application-demo.yml` (criar);
   `package.json` (alterar)
   - `@Profile("demo")`, `ApplicationRunner`. Sequência:
     1. Se já existir a organização de demonstração → aborta com
        `"Já há dados de demonstração. Corre `npm run down` e volta a tentar."` e sai com código 1.
     2. `AuthService.register(...)` cria a organização e o ADMIN; `DemoUserFactory` cria
        `finance@docgrid.local` (FINANCE), `gestor@docgrid.local` (MANAGER) e
        `joao@docgrid.local` (EMPLOYEE). Password comum, escrita no README — vale só para
        o LocalStack desta máquina.
     3. Por cada fatura: `authorizeUpload(...)` (regista e devolve a chave) →
        `storage.put(chave, "application/pdf", pdf)` → **espera** que o documento saia de
        `UPLOADED`/`PROCESSING` (poll ao repositório, timeout de 60 s, mensagem clara ao
        estourar). O worker do mesmo processo é quem processa, pela fila.
     4. Histórico: em ~metade dos `NEEDS_REVIEW`, `FieldCorrectionService.correct(...)`
        seguido de `DocumentApprovalService.approve(...)` com categoria; 3 rejeições com
        motivo (uma delas o duplicado); o resto fica na fila de revisão, para a demonstração
        ao vivo ter o que mostrar.
     5. `ExportService.create(...)` fecha os **dois** meses mais antigos → documentos em
        `EXPORTED` e dois ficheiros CSV no S3.
     6. **Envelhecer o histórico**: um `update` por `JdbcClient` recua `documents.created_at`
        e `document_events.occurred_at` para instantes coerentes com a `issue_date`. Sem
        isto o KPI de tempo médio de revisão do dashboard mede segundos e a lista parece um
        lote único. É a única escrita SQL do seed, e está confinada a este método.
     7. `SpringApplication.exit(context, () -> 0)` — o processo termina, o worker pára.
   - `DemoProperties`: `docgrid.demo.count` (60), `docgrid.demo.password`,
     `docgrid.demo.wait-per-document` (60s).
   - `package.json`: `"seed": "npm run infra && node scripts/mvnw.mjs spring-boot:run -Dspring-boot.run.profiles=local,demo"`.
   - Testes: `backend/src/test/java/com/docgrid/demo/DemoSeedRunnerTest.java` — com
     `docgrid.demo.count=4` contra Postgres + LocalStack (ver
     `backend/src/test/java/com/docgrid/support/LocalStackPipelineConfiguration.java`):
     os 4 documentos chegam a estado final, há eventos de auditoria, e correr o seed
     segunda vez aborta em vez de duplicar.
   - Commit: `feat(demo): npm run seed com 60 documentos e histórico`

7. **ADR retroativo do Postgres** — `docs/adr/0021-postgres-como-armazenamento-unico.md` (criar)
   - Porquê uma base relacional única e não DynamoDB: consultas por fornecedor, período e
     estado; agregações do dashboard; transação entre documento, campos e eventos;
     `jsonb` cobre a geometria sem uma segunda base. Marcar como **retroativo** (a decisão
     é da etapa 01). Alternativas postas de lado: DynamoDB, Mongo, Postgres + Redis.
   - Commit: `docs(adr): Postgres como armazenamento único`

8. **README de vitrine** — `README.md` (reescrever)
   - Ordem obrigatória de `docs/06-DEMO-CHECKLIST.md`: (1) uma frase em linguagem de
     negócio; (2) **o GIF**, `docs/media/review.gif`, acima da dobra; (3) o estado da
     demonstração — não há URL pública, e os dois comandos que a levantam em local, com as
     credenciais; (4) diagrama de arquitetura em Mermaid (browser → CloudFront → API/S3 →
     SQS → worker → Textract → Postgres); (5) o problema, 5 linhas, a partir de
     `docs/01-PRODUCT.md`; (6) o ciclo de vida do documento em `stateDiagram-v2`, gerado a
     partir de `DocumentStatus` — os dois têm de dizer o mesmo; (7) **decisões técnicas**,
     5 parágrafos curtos com link ao ADR: SQS e o worker (0008), idempotência (0007),
     Postgres (0021), confiança visível e revisão humana (0003, 0010), infraestrutura
     escrita e não aplicada (0019); (8) como correr, com `npm run seed`; (9) testes e badges;
     (10) estrutura do repositório; (11) comandos; (12) **limitações conhecidas**, honestas.
   - Fora, por instrução do checklist: lista de tecnologias sem contexto, capturas de código,
     funcionalidades futuras.
   - Commit: `docs: README de vitrine com diagramas`

9. **Índice de ADRs e guiões de media** — `docs/adr/README.md`, `docs/media/README.md`,
   `docs/media/VIDEO.md` (criar)
   - `docs/adr/README.md`: tabela dos 21 ADRs — número, título, **a decisão numa linha**, e
     a etapa em que foi tomada. É isto que responde ao critério "os ADRs explicam as decisões
     sem que seja preciso ler código".
   - `docs/media/README.md`: o percurso exato a filmar para o GIF de 20 s (login como
     `finance@docgrid.local` → fila de revisão → abrir um documento com campo amarelo →
     focar o campo e a bounding box acende → corrigir → aprovar → volta à fila com menos um),
     o recorte, a duração alvo e onde gravar o ficheiro.
   - `docs/media/VIDEO.md`: guião cronometrado — 20 s problema, 60 s demonstração, 40 s
     arquitetura — com o que dizer em cada bloco e o que estar no ecrã.
   - Commit: `docs: índice de ADRs e guiões do GIF e do vídeo`

10. **Verificação em máquina limpa** — sem ficheiros novos
    - `git clone` do repositório para uma pasta nova, `npm run up`, `npm run seed`, `cd
      frontend && npm install && npm run dev`, percorrer login → documentos → revisão →
      dashboard → exportações. Corrigir o que falhar; se for só documentação, commit
      `docs: correções da verificação em máquina limpa`.

## Critérios de aceitação

```bash
npm test                  # 326 + os novos testes do pacote demo, todos verdes
npm run lint              # Spotless limpo
cd frontend && npm run lint && npm run typecheck && npm test && npm run build
gitleaks detect --config .gitleaks.toml --no-banner

npm run down              # base limpa
npm run up                # noutro terminal, fica a correr
npm run seed              # termina com código 0, em menos de 2 minutos
```

Depois do seed, com o frontend em `npm run dev`:

- Login com `finance@docgrid.local` — `/documents` mostra 60 documentos em estados variados.
- `/review-queue` não está vazia e inclui um documento marcado como duplicado, com link ao original.
- Um documento em revisão mostra o **PDF à esquerda** e, ao focar um campo amarelo, a
  bounding box acende no sítio certo.
- `/dashboard` tem gráficos com dados em vários meses, não uma linha só.
- `/exports` tem dois períodos fechados, com CSV descarregável.
- `git clone` numa pasta nova + `npm run up` funciona à primeira.
- O README abre no GitHub com os dois diagramas Mermaid renderizados.

## Fora de âmbito

- Gravar o GIF e o vídeo → do autor, com os guiões deste plano em `docs/media/`.
- `terraform apply`, deploy e URL pública → sem conta AWS, por decisão (ADR 0019).
- Funcionalidades novas, incluindo ecrã de registo e gestão de utilizadores → uma falha que
  apareça documenta-se como limitação conhecida.
- O artigo técnico sobre idempotência com SQS → depois desta etapa, fora do roteiro.

## Desvios durante a execução

Preenchido por **quem implementa, à medida que acontece** — não no fim, não no handoff.

| Passo | O que o plano dizia | O que ficou | Detalhe ou decisão |
|---|---|---|---|
| | | | |
