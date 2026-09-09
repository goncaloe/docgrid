# 0007 — Idempotência do worker: um claim por chave do S3

**Estado:** aceite · **Data:** 2026-09-09

## Contexto

O SQS entrega *at-least-once*: a mesma mensagem chega mais que uma vez, e duas entregas
podem estar em curso ao mesmo tempo. Sem defesa, o worker processa a mesma fatura duas
vezes — dois conjuntos de campos extraídos, dois eventos de auditoria, e na etapa 04 duas
chamadas pagas ao Textract.

A entrega repetida não é um caso raro a tolerar: é o modo de funcionamento normal. O
sistema tem de a tratar como rotina, não como erro.

Há ainda um segundo problema, mais subtil: uma entrega que começou a processar e foi
interrompida a meio (o worker caiu, a mensagem voltou por timeout de visibilidade). A
entrega seguinte tem de saber a diferença entre "isto já está feito" e "isto ficou a
meio, retoma".

## Decisão

Uma tabela `processing_claims` com a **chave do S3 como chave primária**. Antes de
processar, o worker insere lá uma linha, na mesma transação que passa o documento de
`UPLOADED` a `PROCESSING`. Quem ganha o `insert` é quem processa; as entregas concorrentes
apanham a violação da chave primária e voltam a ler o estado para decidir o que fazer.

A coluna `completed_at` distingue os dois casos difíceis:

- **nula** — o trabalho está em curso ou foi interrompido. O documento está em
  `PROCESSING`. A entrega retoma.
- **preenchida** — o trabalho terminou (o preenchimento acontece na transação que passa o
  documento a `EXTRACTED`). Duplicado verdadeiro: nada a fazer, apaga-se a mensagem.

A chave é a do S3, e não o `messageId` do SQS, porque é a identidade do *trabalho*, não a
do *transporte*: o `messageId` muda quando a mensagem é reprocessada a partir da DLQ, e
uma chave do S3 corresponde a um e a um só documento (`uq_documents_storage_key`).

O reprocessamento manual (`FAILED → PROCESSING`, acionado a partir da DLQ) reabre o claim
— `completed_at` volta a nulo — para a entrega seguinte o ver incompleto e retomar em vez
de o tratar como duplicado.

## Alternativas consideradas

**Só verificação de estado (`document.status != UPLOADED` ⇒ já tratado).** Não precisa de
tabela nova. Mas: (1) é uma corrida — duas entregas leem `UPLOADED` antes de qualquer uma
escrever `PROCESSING`, e resolvê-lo exige `SELECT ... FOR UPDATE` ou bloqueio otimista;
(2) mesmo resolvida a corrida, o estado sozinho não distingue "este `PROCESSING` é meu" de
"é de um duplicado", nem "concluído" de "interrompido". O estado é a máquina de ciclo de
vida do documento; misturar-lhe a deduplicação do transporte sobrecarrega-o.

**Tabela genérica de mensagens processadas, chave = `messageId` do SQS** (o padrão
"inbox"). O `messageId` é a chave errada aqui: um redrive da DLQ produz um `messageId`
novo para o mesmo objeto, portanto a mensagem reprocessada passaria a limpo a
deduplicação e o trabalho far-se-ia outra vez. E a tabela cresce sem limite — precisa de
um TTL e de um trabalho de limpeza. A tabela de claims tem uma linha por objeto, limitada
pelo número de documentos.

**Restrição de unicidade em `extracted_fields` e apanhar a violação.** Funciona como
última linha de defesa, mas só *depois* do trabalho caro — o duplicado paga na mesma a
leitura do S3 e, na etapa 04, o Textract. O claim verifica-se antes.

## Consequências

**Torna fácil:** a corrida resolve-se com uma restrição de chave primária, atomicamente,
sem bloqueio explícito na base de dados nem na aplicação. A distinção
"duplicado / retomar / desistir" fica legível numa coluna. O custo do processamento
duplicado evita-se antes de ser pago.

**Torna difícil:** há uma tabela e uma entidade a mais, e o `DocumentProcessor` gere três
transações à mão (reclamar, trabalhar, escrever) em vez de um `@Transactional` por método
— o trabalho pesado (S3, extração) não pode segurar uma ligação à base de dados aberta.
Quem reprocessa um documento tem de se lembrar de reabrir o claim; se não o fizer, a
segunda passagem vê um claim concluído e trata a mensagem como duplicado. O
`DlqAdmin.redriveAll` já o faz; um futuro botão de "reprocessar" por documento (etapa 06)
tem de o fazer também.
