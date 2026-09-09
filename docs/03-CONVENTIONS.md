# DocGrid — Convenções

## Estrutura do backend

Organizado por funcionalidade, não por camada técnica. Nada de `controllers/`, `services/`,
`repositories/` no topo — isso espalha uma alteração por cinco pastas.

```
com.docgrid
├── document/        # upload, estados, consulta
├── extraction/      # interface + Textract + stub
├── validation/      # motor de regras
├── supplier/
├── export/
├── auth/
├── shared/          # config, exceções, utilitários
└── DocGridApplication.java
```

Dentro de cada pacote: `XController`, `XService`, `XRepository`, `XEntity`, `dto/`.
O que é interno ao pacote é package-private. Só sai público o que outro pacote usa mesmo.

## Regras de código

- Java 21. Records para DTOs, `sealed` onde ajudar, sem Lombok (menos magia, mais legível
  para quem avalia o código).
- Entidades JPA nunca saem do pacote. Conversão para DTO na fronteira.
- Sem `Optional` em campos de entidades nem em parâmetros. Só em retornos.
- Dinheiro é `BigDecimal` com escala 2. Nunca `double`. Nunca.
- Datas: `LocalDate` para datas de fatura, `Instant` para timestamps de sistema.
- Exceções de domínio próprias, tratadas num `@RestControllerAdvice` que devolve
  RFC 7807 (`application/problem+json`).
- Nada de `System.out.println`. SLF4J com logging estruturado.

## Testes

| Tipo | Ferramenta | O que cobre |
|---|---|---|
| Unitário | JUnit 5 + AssertJ | Regras de validação, cálculos, NIF, máquina de estados |
| Integração | Testcontainers (Postgres + LocalStack) | Repositórios, fluxo de upload, consumo da fila |
| API | MockMvc | Contratos REST, autorização, códigos de erro |
| Frontend | Vitest + Testing Library | Componentes com lógica, hooks |

Regra prática: **toda a regra de negócio tem teste unitário.** Todo o endpoint tem pelo
menos um teste de autorização (utilizador sem permissão recebe 403).

Nunca uses mocks para o Postgres ou para o S3 — usa Testcontainers e LocalStack.
Testes que passam contra um mock e falham contra a realidade não valem nada.

## Commits

Conventional Commits, em português. A mensagem de commit é explicação, e as explicações
deste projeto são em português; o tipo e o âmbito ficam em inglês por serem convenção da
ferramenta, tal como os nomes no código.

```
feat(extraction): adiciona limiar de confiança à validação de campos
fix(document): evita processamento duplicado na reentrega do SQS
test(validation): cobre casos limite do dígito de controlo do NIF
docs(adr): regista a decisão de usar SQS em vez de processamento direto
chore(ci): adiciona cache de Testcontainers ao GitHub Actions
```

Um commit por unidade coerente de trabalho. Não faças um commit gigante no fim da etapa —
quem avalia o repositório lê o histórico e um histórico legível é sinal de maturidade.

## ADRs

Decisões com alternativa defensável vão para `docs/adr/NNNN-titulo-curto.md`:

```markdown
# 0003 — Processamento assíncrono com SQS

**Estado:** aceite · **Data:** 2026-XX-XX

## Contexto
[o problema, em 3 a 5 linhas]

## Decisão
[o que ficou decidido]

## Alternativas consideradas
[o que foi posto de lado e porquê]

## Consequências
[o que isto torna fácil, o que torna difícil]
```

Cinco a oito ADRs bem escritos no fim do projeto valem mais numa entrevista do que
mil linhas de código extra.

## Frontend

- TypeScript estrito. Sem `any`.
- TanStack Query para estado de servidor. Estado local com `useState`/`useReducer`.
  Sem Redux — não há aqui complexidade que o justifique.
- Componentes em `features/<funcionalidade>/`, espelhando o backend.
- Tipos da API gerados ou copiados de uma única fonte, nunca redeclarados ad-hoc.
- Acessibilidade: labels em todos os inputs, foco visível, navegação por teclado na
  fila de revisão (é a tela mais usada).
