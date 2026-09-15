# Contribuir para o DocGrid

O DocGrid é um projeto de portefólio, não um produto comercial. Ainda assim, quem quiser
abrir uma issue ou um pull request é bem-vindo — e quem quiser apenas correr o projeto
encontra aqui o caminho mais curto.

## Correr o projeto

Precisas de **Docker**, **JDK 21** e **Node 20.6+**. O Maven não é preciso: o repositório
traz o Maven Wrapper.

```bash
npm run up      # infraestrutura (Postgres + LocalStack) e a aplicação no perfil local
npm run seed    # 60 faturas de demonstração, com histórico
```

Frontend, noutro terminal:

```bash
cd frontend && npm install && npm run dev
```

Antes de abrires um pull request:

```bash
npm test                                  # testes do backend, com Testcontainers
npm run lint                              # formatação (Spotless)
cd frontend && npm run lint && npm test   # ESLint e Vitest
```

Usa sempre os comandos `npm` do topo do repositório, nunca o `mvn` ou o `docker compose`
por baixo deles — a razão está no [ADR 0002](docs/adr/0002-ambiente-local-e-comandos.md).

## Idiomas

**Português nas explicações**, no README, na documentação e nas mensagens de commit.
**Inglês no código**: nomes de classes, métodos, variáveis, tabelas e colunas.

A regra não é estética. As explicações são para se lerem; o código é para se ler com as
bibliotecas à volta, que estão todas em inglês.

## Commits

[Conventional Commits](https://www.conventionalcommits.org/), com a mensagem em português
e o tipo e o âmbito em inglês:

```
feat(extraction): adiciona limiar de confiança à validação de campos
fix(document): evita processamento duplicado na reentrega do SQS
test(validation): cobre casos limite do dígito de controlo do NIF
docs(adr): regista a decisão de usar SQS em vez de processamento direto
```

Um commit por unidade coerente de trabalho, com os testes junto da funcionalidade que
testam. Sem co-autoria de ferramentas, sem link de sessão, sem número de issue: a mensagem
descreve a alteração e mais nada.

## Quando se escreve um ADR

Sempre que a decisão tiver **uma alternativa defensável** — outra biblioteca, outro modelo
de dados, outro desenho de fluxo. Nesse caso escreve-se um registo curto em
`docs/adr/NNNN-titulo.md`, em vez de a decisão ficar enterrada no código.

O formato está em [`docs/03-CONVENTIONS.md`](docs/03-CONVENTIONS.md): contexto, decisão,
alternativas consideradas, consequências. Quatro secções, meia página.
O [índice dos ADRs](docs/adr/README.md) diz o que cada um decide.

## Testes

- Toda a regra de negócio tem teste unitário.
- Todo o endpoint tem pelo menos um teste de autorização.
- Postgres e S3 nunca se substituem por mocks: usam-se Testcontainers e LocalStack. Um
  teste que passa contra um mock e falha contra a realidade não vale nada.

## Onde vivem as regras

O ficheiro [`AGENTS.md`](AGENTS.md) é a fonte única das regras do repositório — stack,
convenções, âmbito, o que se pode e o que não se pode trocar. Os ficheiros de ferramentas
(como o `CLAUDE.md`) importam-no e não repetem nada dele.

A documentação de referência está em `docs/`: produto (`01`), arquitetura (`02`),
convenções (`03`), roteiro (`04`).
