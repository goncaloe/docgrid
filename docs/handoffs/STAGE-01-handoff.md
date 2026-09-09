# Handoff — Etapa 01: Domínio e persistência

**Data:** 2026-09-09 · **Sessão:** #2 · **Estado:** completa

## O que ficou feito

- **Três migrações Flyway**, uma por agregado, em
  `backend/src/main/resources/db/migration/`:
  - `V2__organizations_and_users.sql` — `organizations`, `users`. Email único em todo o
    sistema por `lower(email)`, não por organização.
  - `V3__documents.sql` — `documents`, `extracted_fields`, `validation_results`,
    `document_events`. É a migração central da etapa, com os quatro índices de `documents`
    e as constraints que sustentam o modelo.
  - `V4__suppliers.sql` — `suppliers`, com unicidade por `(organization_id, tax_id)`.
- **Entidades JPA** nos pacotes por funcionalidade que já existiam. Todas package-private,
  todas a referir outros agregados por `UUID` em vez de `@ManyToOne`. As chaves
  estrangeiras existem na base de dados; o Java não carrega o que não precisa.
- **`DocumentStatus`** (`backend/src/main/java/com/docgrid/document/DocumentStatus.java`)
  com a tabela de transições dentro do próprio enum. Onze transições válidas em sessenta e
  quatro pares. `Document.transitionTo` valida e lança
  `InvalidStatusTransitionException`; **não existe `setStatus`**.
- **Auditoria em `document_events`** a cada transição, escrita por
  `DocumentService.transition` na mesma transação da mudança de estado.
- **Repositórios Spring Data** com as consultas derivadas do nome que as etapas 02, 03, 05
  e 06 vão precisar. Nenhuma `@Query` de agregação — isso é etapa 09.
- **`shared/BaseEntity`** — id `uuid` gerado no construtor, `created_at`/`updated_at` por
  `@PrePersist`/`@PreUpdate`, `equals`/`hashCode` com `Hibernate.getClass`.
  **`shared/DomainException`** — a base que a etapa 06 vai traduzir para RFC 7807.
- **Infraestrutura de testes partilhada** em `backend/src/test/java/com/docgrid/support/`:
  `PostgresContainerConfiguration` (o container como bean) e a anotação `@RepositoryTest`.
  O `ApplicationContextTest` da etapa 00 passou a usá-la.
- **`docs/01-PRODUCT.md`** — o ciclo de vida passou de desenho ASCII ambíguo a lista
  explícita de transições, com `EXTRACTED → REJECTED` acrescentado.

## Decisões tomadas

| Decisão | Alternativa posta de lado | Porquê |
|---|---|---|
| Estado em `varchar(20)` com `CHECK` | Tipo enum nativo do Postgres; inteiro | Acrescentar um valor é uma migração de duas linhas em vez de `ALTER TYPE`; remover não obriga a recriar o tipo e todas as colunas que o usam; o Hibernate não precisa de `@JdbcTypeCode(SqlTypes.NAMED_ENUM)`. Um inteiro pouparia espaço e tornaria a base ilegível sem consultar o código. |
| `extracted_fields` como verdade **e** colunas de projeção em `documents` | Só colunas em `documents`; só `extracted_fields`; versões com `superseded_at`; `jsonb` | Só colunas não tem onde guardar a confiança, e sem confiança por campo a interface deixa de poder dizer onde olhar. Só `extracted_fields` deixaria a regra do duplicado (05) e as agregações (09) sobre pivots, e o índice por NIF+número não teria onde assentar. ADR 0003. |
| Histórico das correções em `document_events` | Versões em `extracted_fields` | Versões obrigariam todas as leituras a filtrar pela corrente, e uma consulta futura que se esqueça desse filtro não falha — devolve valores antigos silenciosamente. |
| Transições dentro do enum `DocumentStatus` | Classe de serviço à parte; Spring Statemachine | São oito estados e onze transições, cabem em quinze linhas, e dentro do enum testam-se sem levantar o Spring. Uma biblioteca acrescentaria vocabulário sem tornar nada mais claro. |
| Auditoria escrita à mão no método da transição | `@EntityListeners`; eventos de domínio do Spring Data | Um listener não sabe **quem** provocou a mudança nem **porquê** — e são essas duas colunas que dão valor a `document_events`. A garantia de nunca esquecer não compensa perder metade da informação. |
| Nunca apagar documentos; utilizadores com `deactivated_at` | Soft delete com `@SQLDelete` e `@SQLRestriction` | Um filtro global invisível faz uma consulta escrita seis meses depois devolver menos linhas sem nada no código a explicar porquê. Estados terminais fazem o mesmo à vista de todos. |
| `EXTRACTED → REJECTED` acrescentado ao ciclo de vida | Ficar pelo que o diagrama dizia | Um documento pode estar tudo verde e simplesmente não ser uma fatura. Passar por `NEEDS_REVIEW` só para poder ser recusado é burocracia sem valor. `docs/01-PRODUCT.md` foi corrigido na mesma alteração. |
| `id uuid` gerado no construtor | `bigserial`; UUID gerado pelo Hibernate | O id existe antes do primeiro `flush`, portanto `equals`/`hashCode` funcionam em coleções, e a etapa 02 pode construir a chave do S3 sem gravar a linha primeiro. Evita enumeração de ids na API da etapa 06. |
| `document_events.id` em `bigint identity` | `uuid`, como as outras | É um log append-only: a ordem de inserção tem significado e o id nunca sai numa URL. É também o desempate de `findByDocumentIdOrderByOccurredAtAscIdAsc`. |
| `vat_rate` como percentagem (`23.00`) | Fração (`0.23`) | É como aparece na fatura e como o contabilista a espera no CSV da etapa 09. |
| Uma migração por agregado (V2, V3, V4) | Uma migração única para a etapa | Dá três commits com sentido próprio em vez de um bloco. `documents` não tem chave estrangeira para `suppliers`, portanto a ordem entre V3 e V4 não impõe nada. |
| Container Postgres como `@Bean` numa `@TestConfiguration` | `@Container` estático numa classe base abstrata | Com `@Container` o Testcontainers arranca e pára o container **por classe de teste**. Como bean, o container segue o contexto Spring, que é reaproveitado entre classes: nove classes de teste, dois containers. |

ADRs escritos: `docs/adr/0003-desenho-de-extracted-fields.md`,
`docs/adr/0004-estados-do-documento-e-auditoria.md`

## Como verificar

Testes e formatação não precisam do compose — o Testcontainers levanta o seu próprio
Postgres. Ambos foram corridos nesta sessão:

```bash
npm run lint
# BUILD SUCCESS

npm test
# Tests run: 110, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS
```

Distribuição real: 76 em `DocumentStatusTest` (a matriz de 64 pares mais 12 casos
dirigidos), 6 em `DocumentTest`, 7 em `DocumentRepositoryTest`, 5 em `DocumentServiceTest`,
5 em `ExtractedFieldRepositoryTest`, 4 em `SupplierRepositoryTest`, 3 em
`ValidationResultRepositoryTest`, 2 em `DocumentEventRepositoryTest`, 2 em
`ApplicationContextTest`.

Durante a corrida aparecem quatro linhas `ERROR ... duplicate key value violates unique
constraint`. **São esperadas**: são os quatro testes que provam que as constraints de
unicidade recusam o que devem recusar.

O ciclo do zero, que prova o primeiro critério de aceitação — migrações aplicadas num
Postgres vazio. Precisas do Docker Desktop a correr:

```bash
npm run down && npm run up
```

Fica em primeiro plano. Noutro terminal:

```bash
curl localhost:8080/actuator/health
# {"status":"UP"}

docker exec docgrid-postgres psql -U docgrid -d docgrid -tAc \
  "select version, description, success from flyway_schema_history order by installed_rank;"
# 1|init|t
# 2|organizations and users|t
# 3|documents|t
# 4|suppliers|t

docker exec docgrid-postgres psql -U docgrid -d docgrid -tAc \
  "select tablename from pg_tables where schemaname='public' order by tablename;"
# document_events, documents, extracted_fields, flyway_schema_history,
# organizations, suppliers, users, validation_results
```

A aplicação arrancar já é meia verificação: com `ddl-auto: validate`, qualquer divergência
entre as entidades JPA e as migrações impede o contexto de subir.

As constraints à prova, contra a base a correr — este comando foi executado e é esta a
resposta que dá:

```bash
docker exec docgrid-postgres psql -U docgrid -d docgrid -tAc \
  "insert into documents (id, organization_id, submitted_by, storage_key, original_filename,
   content_type, status, currency, created_at, updated_at) values (gen_random_uuid(),
   gen_random_uuid(), gen_random_uuid(), 'x', 'x.pdf', 'application/pdf', 'INVENTADO',
   'EUR', now(), now());"
# ERROR: new row for relation "documents" violates check constraint "ck_documents_status"
```

Critérios de aceitação do briefing:

- [x] `npm run up` aplica todas as migrações num Postgres vazio
- [x] `APPROVED → PROCESSING` lança exceção de domínio —
      `DocumentTest.refusesToMoveAnApprovedDocumentBackToProcessing`
- [x] Cada transição escreve um evento de auditoria —
      `DocumentServiceTest.writesAnAuditEventForEveryTransition`
- [x] Um campo extraído guarda valor, confiança e origem — `ExtractedFieldRepositoryTest`
- [x] Existe um ADR sobre o desenho de `extracted_fields` — `docs/adr/0003-*`

## O que ficou por fazer

Nada em falta bloqueia a etapa 02. Fora de âmbito por decisão, não por esquecimento:

- **Tabela `exports`** — etapa 09. Consta de `docs/02-ARCHITECTURE.md` mas não do âmbito
  desta etapa.
- **Coluna de bounding box em `extracted_fields`** — etapa 04, que é onde se decide como
  as guardar.
- **Índice por `issue_date`** — etapa 09, quando existirem agregações mensais que o
  justifiquem.
- **Motor de regras, dígito de controlo do NIF** — etapa 05. A tabela `validation_results`
  está de pé e vazia.
- **Autenticação** — etapa 06. `users.password_hash` e `users.role` existem porque a
  tabela é desta etapa; nenhum código lhes toca.
- **Criação de documentos** — etapa 02. `Document` tem construtor e os testes usam-no, mas
  não há serviço que registe um documento nem quem escreva o evento `CREATED`.

Dois métodos foram escritos nesta etapa apesar de quem os vai chamar a sério ser uma etapa
futura. Ficam assinalados de propósito, para não parecerem esquecimento nem descuido de
âmbito:

- **`Document.projectInvoiceFields(InvoiceFields)`** — sem ele as colunas de projeção não
  teriam como ser escritas a partir do Java, e o índice `idx_documents_org_supplier_invoice`
  não teria como ser testado. Quem o chama a sério é a etapa 04.
- **`DocumentEvent.fieldCorrected(...)`** — sem ele a coluna `payload jsonb` ficaria
  mapeada sem nunca ser exercitada, e o mapeamento `@JdbcTypeCode(SqlTypes.JSON)` só falha
  em execução. `DocumentEventRepositoryTest` prova que atravessa o Hibernate e volta. Quem
  o liga ao fluxo de correção é a etapa 06.

## Armadilhas para a próxima sessão

1. **`@DataJpaTest` precisa de `@AutoConfigureTestDatabase(replace = NONE)`.** Sem isso o
   Spring troca o container por uma base embutida e o teste deixa de provar o que diz
   provar — e passa na mesma, que é o pior dos casos. Está encapsulado em `@RepositoryTest`;
   usa a anotação em vez de montar o slice à mão.
2. **A tradução de exceções vive no proxy do repositório, não no `EntityManager`.**
   `entityManager.flush()` lança `jakarta.persistence.PersistenceException`;
   `repository.flush()` ou `repository.saveAndFlush()` lançam
   `DataIntegrityViolationException`. Para afirmar sobre uma constraint violada, passa pelo
   repositório.
3. **`created_at` é atribuído no `@PrePersist`, que só corre no flush.** Com id atribuído
   pela aplicação, o `persist()` não vai à base de dados e o Hibernate adia o insert. Dois
   documentos persistidos sem flush pelo meio ficam com o mesmo instante e qualquer teste
   de ordenação por `created_at` passa a ser uma moeda ao ar. `DocumentRepositoryTest` usa
   `persistAndFlush` por documento por causa disto.
4. **As entidades são package-private, portanto as fixtures vivem no pacote de teste
   correspondente.** `com.docgrid.auth.AuthFixtures` e `com.docgrid.document.DocumentFixtures`
   são públicas e devolvem apenas `UUID`. Um pacote novo que precise de um documento chama
   `DocumentFixtures`; não tentes importar `Document`, não sai de lá.
5. **A ordem de imports do Spotless é `java, javax, jakarta, <resto>, com.docgrid`.**
   Escrever imports por ordem alfabética falha o build na fase `validate`, antes de chegar
   aos testes. `npm run format` corrige.
6. **Dois contextos Spring, dois containers Postgres.** `@SpringBootTest` e o slice
   `@DataJpaTest` são configurações diferentes, logo o Spring não partilha o contexto entre
   elas. Um teste novo com um slice diferente traz um terceiro container e mais dez
   segundos por corrida.
7. **`document_events` não tem `on delete cascade`, de propósito.** Apagar um documento
   falha por chave estrangeira, e isso é a garantia de retenção, não um esquecimento. Se um
   dia for preciso remover dados pessoais, anonimiza os campos — não apagues a linha.
8. **`ck_extracted_fields_confidence_required` é rígida por opção.** Um campo com origem
   `AI` **tem** de trazer confiança, e um com origem `HUMAN` **não pode** trazer nenhuma.
   Se a etapa 04 ou a 06 quiserem guardar um campo humano com confiança `1.0`, a constraint
   tem de mudar por migração — não é um detalhe que se contorne em Java.
9. **`DocumentStatus` e `docs/01-PRODUCT.md` têm de dizer o mesmo, e nada o verifica
   automaticamente.** `DocumentStatusTest` tem a lista de transições escrita à mão, de
   propósito — derivá-la do enum tornaria o teste uma tautologia que passaria com qualquer
   tabela, incluindo uma errada. Ao acrescentar uma transição, mexe nos três sítios.
10. **As colunas de projeção em `documents` têm um só escritor,
    `Document.projectInvoiceFields`.** Nada no compilador o impõe. Se a etapa 04 escrever
    essas colunas por outro caminho, as duas representações divergem em silêncio — é o
    custo assumido no ADR 0003 e o ponto a olhar em revisão de código.

## Ficheiros centrais desta etapa

- `backend/src/main/resources/db/migration/V3__documents.sql` — o agregado documento
  inteiro. Se houver um ficheiro para ler antes de qualquer outro, é este.
- `backend/src/main/java/com/docgrid/document/DocumentStatus.java` — o ciclo de vida e as
  transições permitidas, em quinze linhas.
- `backend/src/main/java/com/docgrid/document/Document.java` — o agregado. `transitionTo`
  é o único caminho para mudar de estado.
- `backend/src/main/java/com/docgrid/document/DocumentService.java` — onde a transição e o
  evento de auditoria ficam presos um ao outro, na mesma transação.
- `backend/src/main/java/com/docgrid/document/ExtractedField.java` — valor, confiança e
  origem, com dois construtores nomeados que tornam impossível trocá-los.
- `backend/src/main/java/com/docgrid/shared/BaseEntity.java` — id e marcas temporais de
  todas as entidades de negócio.
- `backend/src/test/java/com/docgrid/support/RepositoryTest.java` — a anotação que todo o
  teste de persistência desta etapa em diante deve usar.
- `backend/src/test/java/com/docgrid/document/DocumentStatusTest.java` — a matriz de
  sessenta e quatro pares.
- `docs/adr/0003-desenho-de-extracted-fields.md` — porque há duas representações do mesmo
  valor, e o que isso custa.

## Commits

```
bf4b3a4 feat(db): esquema de organizações e utilizadores
82a7366 feat(document): estados do documento e máquina de transições
c521d71 feat(db): esquema de documentos, campos extraídos, validações e eventos
aa73cf8 feat(document): entidades e repositórios do agregado documento
83c3011 feat(document): transições com registo automático de auditoria
0e7ea09 feat(supplier): esquema e entidade de fornecedores
fe7c4b6 docs: acrescenta a transição EXTRACTED → REJECTED ao ciclo de vida
cb3a786 docs(adr): desenho de extracted_fields, estados e auditoria
```
