# 0021 — Postgres como armazenamento único

**Estado:** aceite · **Data:** 2026-09-15 · **Retroativo:** a decisão é da etapa 01

> Escrito na etapa 12. A decisão foi tomada quando o esquema nasceu e nunca chegou a ser
> registada — e é das primeiras perguntas que alguém faz ao ver um sistema com S3, SQS e
> uma fila de processamento: *porquê uma base relacional, e não DynamoDB?*

## Contexto

O DocGrid guarda quatro coisas com formas diferentes:

- **Documentos** — a projeção de negócio de cada fatura (fornecedor, NIF, número, data,
  base, IVA, total, estado), que é o que se lista, filtra e agrega.
- **Campos extraídos** — uma linha por campo, com a confiança e o polígono onde foi lido
  (ADR 0003). O polígono é uma estrutura aninhada, sem esquema fixo.
- **Eventos** — o histórico imutável de tudo o que aconteceu a cada documento (ADR 0004).
- **Resultados de validação** — o que cada regra disse sobre cada documento.

E faz-lhes perguntas de três tipos: pelo id (a revisão de um documento), por critério
(todos os documentos deste fornecedor, deste período, neste estado) e agregadas (o total
por mês, por categoria, os cinco maiores fornecedores — ADR 0013).

A stack já tem serviços AWS: S3 para os ficheiros, SQS para a fila. A pergunta é se a
persistência dos dados também devia ser um serviço gerido de outra natureza.

## Decisão

**Uma base relacional única — PostgreSQL 16 — para tudo o que não é ficheiro.** Os ficheiros
ficam no S3, que é o sítio deles; tudo o resto vive no Postgres, com esquema versionado por
Flyway e chaves estrangeiras a sério.

Três consequências do desenho seguem daqui:

- A geometria dos campos é `jsonb` numa coluna de `extracted_fields`, e não uma segunda
  base de dados de documentos. O Postgres indexa e consulta `jsonb`; o que ali está nunca
  precisa de ser procurado, só lido com o campo.
- As agregações do dashboard são SQL sobre as mesmas tabelas (ADR 0013), sem cópia nem
  processo de sincronização.
- A escrita de um documento processado — a projeção, os campos, a validação, o evento e a
  conclusão do claim — é **uma transação**. Ou acontece tudo, ou não acontece nada, e é isso
  que torna o worker idempotente sem inventar compensações (ADR 0007).

## Alternativas consideradas

**DynamoDB.** É o reflexo natural num sistema com S3 e SQS, e resolveria bem o acesso por
id. Mas as consultas deste produto são quase todas por critério e por intervalo — "faturas
deste fornecedor, entre março e junho, ainda por aprovar" — e no DynamoDB cada uma dessas
perguntas exige desenhar um índice secundário à medida, ou varrer a tabela. As agregações do
dashboard (somas por mês, por categoria, top de fornecedores) não têm resposta direta: ou se
mantém uma tabela de contadores atualizada a cada escrita, ou se exporta para outro sítio
para as calcular. E a transação que escreve documento, campos, validações e evento de uma só
vez — o coração da idempotência — passaria a ser uma coreografia. Trocava-se uma escala que
este sistema não tem por complexidade que teria já.

**MongoDB.** Encaixaria bem no documento extraído, que é naturalmente aninhado, e resolveria
a geometria sem `jsonb`. Mas o resto do modelo é relacional de verdade: um documento
pertence a uma organização, foi submetido por um utilizador, tem N campos e N eventos, e
nenhum desses laços pode ficar solto. Seria uma base de documentos a imitar chaves
estrangeiras em código de aplicação.

**Postgres + Redis para as agregações.** Uma peça a mais para um problema que ainda não
existe: as consultas do dashboard correm em milissegundos sobre dezenas de milhares de
linhas, com os índices que a etapa 09 acrescentou. Cache introduz invalidação, e invalidação
introduz dados errados no ecrã que se usa para decidir. Fica para quando o `EXPLAIN` pedir.

## Consequências

**Torna fácil:** consultar por qualquer combinação de critérios sem planear o índice com
meses de antecedência; agregar sem copiar dados para lado nenhum; garantir integridade
referencial (não há campo extraído sem documento, nem evento sem documento); testar contra
a base verdadeira, em Testcontainers, sem simular nada (ADR 0020).

**Torna difícil:** escalar escritas para além do que uma instância aguenta — a saída seria
réplicas de leitura e, muito depois, particionar por organização. Também obriga a uma
migração Flyway sempre que o modelo muda, o que é trabalho a mais nas primeiras semanas de
um projeto e uma rede de segurança em todas as outras.

**Custo:** em AWS é uma instância RDS `db.t4g.micro` (~15 USD/mês, ver `docs/COSTS.md`),
contra o modelo por pedido do DynamoDB, que a esta escala seria mais barato. É o preço de
poder fazer perguntas que ainda não se sabe que se vão querer fazer.
