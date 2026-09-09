# 0008 — Processamento assíncrono com SQS, e o worker como perfil

**Estado:** aceite · **Data:** 2026-09-09

## Contexto

A extração de uma fatura demora 5 a 30 segundos (etapa 04, com o Textract). Fazê-la dentro
do pedido HTTP de upload prende a ligação todo esse tempo e perde o trabalho inteiro se
algo falhar a meio, sem deixar registo do que reprocessar.

O `docs/02-ARCHITECTURE.md` já tinha decidido "fila SQS" em traço grosso. Esta etapa
concretiza e havia três escolhas dentro dela: que mecanismo de fila, como consumir a fila,
e onde vive o processo que a consome.

## Decisão

### A fila é o SQS

Entrega *at-least-once* com timeout de visibilidade, contagem de entregas e dead-letter
queue nativas; notificação de eventos `s3:ObjectCreated:*` do S3 diretamente para a fila,
sem cola; o LocalStack fala o mesmo protocolo, portanto o desenho corre igual em local e
em AWS; e já está na stack fixa.

Parâmetros da fila (criados pela infraestrutura — script de init em local, Terraform na
etapa 11 —, não pela aplicação):

- **timeout de visibilidade 120 s** — a extração demora 5 a 30 s; 120 s dão folga sem
  fazer um retry esperar muito.
- **`maxReceiveCount` 3** — três entregas falhadas e a mensagem vai para a DLQ. Tem de
  bater certo com o `docgrid.queue.max-receive-count` da aplicação.
- **retenção da DLQ 14 dias** — o máximo do SQS. Uma mensagem que lá pára só sai por
  decisão de alguém.
- **long polling 10 s** — uma espera vazia é barata; um ciclo apertado é puro custo.

### O consumo é à mão, sobre o `SqsClient`

E não o `@SqsListener` do Spring Cloud AWS. Esta etapa existe para mostrar a mecânica que
o listener esconde — visibilidade, `ApproximateReceiveCount`, o ack como decisão, o limiar
da DLQ. O `@SqsListener` traria uma dependência que não está na stack e resolveria por
baixo do pano exatamente aquilo que é o valor de portefólio desta etapa. O ciclo manual
são umas dezenas de linhas (`DocumentWorker`, `SqsQueues`), todas debaixo de teste.

### O worker é um perfil, não um módulo Maven

`@Profile("worker")` no mesmo código-base. A API e o worker partilham o modelo de domínio
inteiro, as migrações, a camada de armazenamento — um módulo à parte obrigaria a um módulo
partilhado e a três `pom.xml` para nenhum ganho de isolamento a esta escala.

Correm como processos separados: em AWS, dois serviços ECS (`aws` para a API, `aws,worker`
para o worker); em local, um só JVM, com o grupo de perfis `local: worker` a ligar os
dois. Escalam independentemente e um pico de processamento não degrada a API.

### Erro transitório repete; erro permanente vai direto para a DLQ

- **Permanente** — evento ilegível, objeto que não existe depois de um `ObjectCreated` (o
  S3 é fortemente consistente), evento de um bucket ou de uma chave sem documento. Não se
  repete: a mensagem não é apagada, esgota as entregas depressa e fica na DLQ para ser
  inspecionada; onde faz sentido, o documento passa a `FAILED`.
- **Transitório** — tudo o resto (uma falha pontual a falar com o S3, um deadlock na base
  de dados). A mensagem volta à fila; o timeout de visibilidade e a contagem de entregas
  fazem o seu trabalho. À terceira, vai para a DLQ e o documento fica `FAILED` na última
  tentativa.

A etapa 04, ao introduzir o Textract, é que traça a fronteira fina entre "documento
ilegível" (permanente) e "serviço indisponível" (transitório).

## Alternativas consideradas

**Trabalho `@Scheduled` a varrer a base de dados por documentos em `UPLOADED`.** Sem fila,
sem infra nova. Mas não há back-pressure, não há retry nem visibilidade por mensagem,
constrói-se o equivalente à DLQ à mão (uma coluna de tentativas e uma varredura de
`FAILED`), e a notificação do S3 é substituída pela latência do polling. É reinventar o
SQS, pior.

**Processamento assíncrono no próprio pedido (`@Async`, pool de fios).** O trabalho só
vive enquanto o JVM viver. Um deploy ou uma queda perde todas as extrações a meio, sem
registo do que retomar. Contraria a razão de ser da etapa.

**Step Functions ou um motor de workflow.** Exagero para um passo linear (extrair →
validar). Acrescenta um serviço à stack e ao ambiente local. A reconsiderar se o pipeline
ganhar ramos.

**Módulo Maven separado para o worker.** Isolamento real ao nível do build, mas os dois
partilham o domínio, as migrações e o armazenamento — a separação obriga a um módulo
comum e a três poms. Nenhum benefício a este tamanho.

## Consequências

**Torna fácil:** retries, visibilidade e DLQ vêm de graça; a notificação S3→SQS liga o
upload ao processamento sem código de cola; o mesmo desenho corre em LocalStack e em AWS;
API e worker escalam separados.

**Torna difícil:** o consumo manual é código que a equipa mantém — o long polling, o ciclo
de vida do fio, a decisão de ack. A concorrência dentro do worker (processar várias
mensagens em paralelo) fica por fazer de propósito, até a etapa 04 ter trabalho pesado que
a justifique. A coerência entre o `maxReceiveCount` da aplicação e o da política de redrive
da fila é manual: se divergirem, a mensagem vai para a DLQ antes ou depois do que o worker
julga.
