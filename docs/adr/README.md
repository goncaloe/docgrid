# Registos de decisão

Vinte e um. Cada um é uma decisão que tinha alternativa defensável — se não tivesse, estava
no código e não aqui. Esta tabela existe para se perceber **o que foi decidido** sem abrir
nenhum ficheiro; o porquê, esse, está em cada registo, com as alternativas que ficaram pelo
caminho.

| # | Título | A decisão, numa linha | Etapa |
|---|---|---|---|
| [0001](0001-biblioteca-de-componentes-ui.md) | Mantine como biblioteca de componentes | Mantine para componentes e estilos, TanStack Table (headless) para a lista; um só sistema de estilos, sem Tailwind | 07 |
| [0002](0002-ambiente-local-e-comandos.md) | Ambiente local: comandos por npm | Os comandos do projeto são npm scripts na raiz e a aplicação corre fora do compose; sem `Makefile`, que o Windows não garante | 00 |
| [0003](0003-desenho-de-extracted-fields.md) | Campos extraídos em tabela própria | Uma linha por campo em `extracted_fields` (com confiança e origem) é a fonte de verdade; `documents` guarda só a projeção que se consulta e agrega | 01 |
| [0004](0004-estados-do-documento-e-auditoria.md) | Estados em texto, transições no domínio | `varchar` com `CHECK` em vez de enum do Postgres; as transições vivem no enum `DocumentStatus` e cada uma deixa evento — nada se apaga | 01 |
| [0005](0005-upload-com-url-pre-assinado.md) | Upload direto para o S3 | URL pré-assinado `PUT` de 5 minutos, com o `Content-Type` assinado: o ficheiro nunca passa pela API | 02 |
| [0006](0006-documentos-orfaos.md) | Documentos órfãos | Desenha-se a estratégia e não se escreve limpeza automática: exigia uma transição nova na máquina de estados por um problema que ainda não existe | 02 |
| [0007](0007-idempotencia-do-worker.md) | Idempotência do worker | Um `processing_claims` com a chave do S3 como chave primária: quem ganha o `insert` processa, e as entregas repetidas do SQS resolvem-se sozinhas | 03 |
| [0008](0008-escolha-de-sqs-e-desenho-do-worker.md) | Processamento assíncrono com SQS | SQS com DLQ nativa e notificação direta do S3, consumido à mão sobre o `SqsClient`; o worker é um perfil Spring, não outro repositório | 03 |
| [0009](0009-analyze-expense-e-geometria-dos-campos.md) | `AnalyzeExpense` e geometria em `jsonb` | O modelo de despesas do Textract, que já devolve campos tipados com confiança e geometria; ilegível é erro permanente, indisponível é transitório | 04 |
| [0010](0010-motor-de-validacao-e-servico-de-aprovacao.md) | Motor de validação e aprovação | Uma `ValidationRule` por regra, descobertas pelo Spring; duplicado por dois critérios (ficheiro e NIF+número) e a aprovação de gestor decidida no momento de aprovar | 05 |
| [0011](0011-autenticacao-jwt-e-isolamento-por-organizacao.md) | Autenticação JWT e isolamento | Access token JWT de 15 minutos e refresh token opaco guardado por hash; a organização vem sempre do token, nunca do pedido | 06 |
| [0012](0012-render-de-pdf-no-browser.md) | Render de PDF no browser | `react-pdf` para desenhar a página e sobrepor os polígonos normalizados; sem `@mantine/form` nem `@mantine/modals` | 08 |
| [0013](0013-read-model-do-dashboard-e-exportacao.md) | Read model do dashboard | As agregações são SQL num pacote que só lê; escrever numa tabela fá-lo apenas o pacote dono dela, pela sua porta pública | 09 |
| [0014](0014-fecho-de-periodo-e-formato-do-csv.md) | Fecho de período e formato do CSV | Um documento pertence ao mês da `issue_date` (é como o contabilista trabalha), e o fecho é irreversível: `APPROVED → EXPORTED` | 09 |
| [0015](0015-id-de-correlacao.md) | Id de correlação de ponta a ponta | O mesmo id segue do pedido HTTP até aos logs do worker, por dois caminhos (a coluna `correlation_id` e o atributo da mensagem) | 10 |
| [0016](0016-metricas-e-health-checks.md) | Métricas e health checks | Micrometer com quatro métricas de negócio e health indicators próprios por dependência; a readiness inclui só a base de dados | 10 |
| [0017](0017-computacao-e-rede-na-aws.md) | Computação e rede na AWS | EC2 `t4g.micro` com Docker Compose em vez de Fargate (memória e custo), rede sem NAT nem endpoints de interface, e SSM Session Manager em vez de SSH | 11 |
| [0018](0018-fronteira-registo-e-segredos.md) | Fronteira, registo e segredos | CloudFront com duas origens como fronteira única (zero CORS), imagem no ghcr.io com o ECR no desenho, segredos em SSM Parameter Store | 11 |
| [0019](0019-infraestrutura-como-desenho.md) | Infraestrutura como desenho | O Terraform é escrito, validado e **nunca aplicado**: sem conta AWS, fica a um `apply` de ser real, com custo zero | 11 |
| [0020](0020-isolamento-de-dados-nos-testes.md) | Isolamento de dados nos testes | Um `TestExecutionListener` trunca as tabelas antes de cada classe: nenhuma classe herda as linhas de outra, em nenhum sistema de ficheiros | 11 |
| [0021](0021-postgres-como-armazenamento-unico.md) | Postgres como armazenamento único | Uma base relacional para tudo o que não é ficheiro — as perguntas são por critério e agregadas, e a escrita de um documento processado é uma transação | 01 (retroativo) |

## Como se escreve um destes

Quatro secções — contexto, decisão, alternativas consideradas, consequências — em meia
página. O formato está em [`../03-CONVENTIONS.md`](../03-CONVENTIONS.md).

A regra de quando: **sempre que a alternativa for defensável.** Se a escolha for óbvia para
qualquer pessoa com a mesma informação, não é um ADR — é código. Se daqui a três meses
alguém (ou o próprio autor) vai perguntar "porque é que isto é assim?", é um ADR.

Um ADR não se reescreve depois de aceite: se a decisão mudar, escreve-se outro que o
substitua e diz-se isso nos dois.
