# 0004 — Estados em texto, transições no domínio, e nada se apaga

**Estado:** aceite · **Data:** 2026-09-09

## Contexto

O ciclo de vida do documento tem oito estados e é a peça central do sistema: quase todas as
consultas filtram por estado e quase todas as ações do utilizador são transições. Três
decisões tinham de ser tomadas ao mesmo tempo, porque se sustentam umas às outras — como
representar o estado na base de dados, onde vive a regra que decide se uma transição é
legítima, e o que acontece a um documento que já não interessa a ninguém.

## Decisão

### O estado é `varchar` com `CHECK`, não um tipo enum do Postgres

`status varchar(20) not null` com `ck_documents_status` a listar os oito valores, mapeado
com `@Enumerated(EnumType.STRING)`. O mesmo para papéis, severidades, origens e tipos de
evento.

### As transições vivem no enum `DocumentStatus`

Uma tabela `Map<DocumentStatus, Set<DocumentStatus>>` estática dentro do próprio enum, e um
`canTransitionTo`. O agregado chama-a em `Document.transitionTo`, que lança
`InvalidStatusTransitionException` quando a transição não existe. Não há `setStatus`.

### A auditoria é escrita à mão, no mesmo método da transição

`Document.transitionTo` devolve o `DocumentEvent` que descreve a mudança, e
`DocumentService.transition` grava-o na mesma transação. Ou acontecem os dois, ou nenhum.

### Nada se apaga

Documentos não têm delete de nenhuma espécie. `REJECTED` e `EXPORTED` são estados
terminais e cobrem o que um delete faria. Um documento aprovado que esteja errado não se
reabre: cria-se um documento de correção. Os utilizadores desativam-se com
`deactivated_at`, porque quem sai da empresa não pode desaparecer sem levar consigo o rasto
de quem submeteu o quê.

## Alternativas consideradas

**Tipo enum nativo do Postgres.** Dá a mesma integridade com menos espaço em disco e
aparece nas ferramentas com o seu nome. Descartado pelo custo de evolução: acrescentar um
valor é `ALTER TYPE`, remover um obriga a recriar o tipo e todas as colunas que o usam, e
o Hibernate precisa de `@JdbcTypeCode(SqlTypes.NAMED_ENUM)` para lhe falar. Um `CHECK`
altera-se numa migração de duas linhas. O espaço poupado, a algumas centenas de documentos
por mês, é irrelevante.

**Estado como inteiro.** Compacto e rápido. Descartado por tornar a base de dados ilegível
sem consultar o código: um `select` que devolve `status = 3` obriga a ir procurar o que é
o 3, e um erro de mapeamento passa despercebido para sempre.

**Máquina de estados numa classe de serviço à parte, ou numa biblioteca (Spring
Statemachine).** Separaria a regra do enum e daria transições configuráveis. Descartado por
ser peso sem retorno: são oito estados e onze transições, cabem em quinze linhas, e dentro
do enum testam-se sem levantar o Spring. Uma biblioteca aqui acrescentaria vocabulário
novo a quem lê o código sem tornar nada mais claro.

**Auditoria por `@EntityListeners` ou por eventos de domínio do Spring Data.** Escreveria o
registo sozinha a cada gravação, sem ninguém se poder esquecer. Descartado porque um
listener não sabe *quem* provocou a mudança nem *porquê* — são as duas colunas que dão
valor a `document_events`. Um histórico que diz "o estado mudou" e não diz por ordem de
quem não serve para nada, e a garantia de nunca esquecer não compensa perder metade da
informação.

**Soft delete com `@SQLDelete` e `@SQLRestriction`.** Manteria a linha e esconderia-a de
todas as consultas automaticamente. Descartado por ser um filtro global invisível: seis
meses depois, alguém escreve uma consulta, obtém menos linhas do que espera e não tem no
código nada que lhe explique porquê. Estados terminais explícitos fazem o mesmo trabalho à
vista de todos.

## Consequências

**Torna fácil:** acrescentar um estado ou uma transição — um valor no enum, uma linha na
tabela de transições, uma migração que alarga o `CHECK`; ler a base de dados sem traduzir
nada; testar o ciclo de vida inteiro sem Spring e sem Postgres, o que mantém a matriz de
sessenta e quatro casos a correr em milissegundos.

**Torna difícil:** garantir que ninguém escreve um evento à mão sem passar pelo serviço.
A porta está fechada — `Document` não expõe `setStatus` e `transitionTo` é package-private
— mas é uma convenção dentro do pacote, não uma barreira do compilador. Se um dia houver
mais do que um sítio a fazer transições, a garantia passa a depender de revisão de código.

E torna difícil apagar dados de propósito. A chave estrangeira de `document_events` para
`documents` não tem `on delete cascade`, exatamente para isso: apagar um documento exigiria
apagar antes o seu histórico, o que ninguém faz por acidente. Se algum dia o RGPD obrigar a
remover dados pessoais de um documento, a resposta é anonimizar os campos, não apagar a
linha.
