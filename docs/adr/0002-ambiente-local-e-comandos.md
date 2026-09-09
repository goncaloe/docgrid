# 0002 — Ambiente local: comandos por npm, aplicação fora do compose

**Estado:** aceite · **Data:** 2026-09-09

## Contexto

A etapa 00 exige que um único comando levante Postgres, LocalStack e a aplicação, e que
`/actuator/health` responda. O plano original pedia um `Makefile` com `up`, `down`, `test`,
`lint` e `logs`.

Duas coisas contrariaram esse plano. O `make` não existe na máquina de desenvolvimento
(Windows 11) nem vem com o Git for Windows — obrigaria a uma instalação extra logo no primeiro
passo, exatamente o atrito que a etapa existe para eliminar. E a decisão de meter a aplicação
dentro do `docker-compose.yml` para cumprir o "um comando" trocava um arranque cómodo por um
ciclo de desenvolvimento lento: cada alteração de código passaria a exigir reconstrução de
imagem.

## Decisão

**Os comandos do projeto são npm scripts na raiz.** O Node já é pré-requisito por causa do
frontend (etapa 07) e não acrescenta nada a instalar. Um `scripts/mvnw.mjs` reencaminha para
o Maven Wrapper, escolhendo `mvnw` ou `mvnw.cmd` conforme a plataforma, e carrega o `.env`
quando existe.

**O `docker-compose.yml` leva só a infraestrutura.** A aplicação corre no host com
`spring-boot:run`. O `npm run up` encadeia as duas coisas — `docker compose up -d --wait` e
depois a aplicação — portanto o "um comando" mantém-se.

O LocalStack só fica saudável depois de o script de init criar o bucket e a fila, e o
`--wait` espera por isso. Quando a aplicação arranca, a infraestrutura está pronta de facto,
não apenas de pé.

## Alternativas consideradas

**Instalar o GNU Make.** Mantinha o `Makefile` como fonte única e é o que qualquer avaliador
em Linux ou macOS espera. Descartado por exigir uma instalação para correr o projeto —
`winget install` antes do primeiro `clone` é o género de passo que faz desistir quem avalia.

**Taskfile (go-task).** Multiplataforma e um só ficheiro, sem a duplicação de scripts por
sistema operativo. Descartado pela mesma razão: continua a ser um binário a instalar.

**Dois scripts, `dg.ps1` e `dg.sh`.** Zero dependências, mas duas implementações dos mesmos
alvos a divergir com o tempo.

**Aplicação dentro do compose, com Dockerfile multi-stage.** Cumpria o critério à letra e o
Dockerfile seria reaproveitado na etapa 11 para o ECS Fargate. Descartado porque o custo cai
onde mais dói — em cada iteração de desenvolvimento. O Dockerfile continua a fazer falta em
produção e será escrito na etapa 11, onde é mesmo preciso.

## Consequências

**Torna fácil:** clonar e correr sem instalar nada além de Docker, JDK e Node; alterar código
e reiniciar em segundos; correr a aplicação no IDE com `npm run infra`, com depuração e
recarregamento normais.

**Torna difícil:** o `npm run up` fica em primeiro plano no terminal, portanto o `curl` de
verificação corre noutro. Não há prova, nesta etapa, de que a aplicação corre em container —
essa garantia só chega na etapa 11, e um erro no Dockerfile só se descobre lá. E fica um
`package.json` na raiz de um projeto Java, que na etapa 07 conviverá com o do `frontend/`;
se a duplicação incomodar, converte-se em workspaces npm.
