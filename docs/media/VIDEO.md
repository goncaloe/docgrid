# Vídeo de dois minutos

Dois minutos, com voz, sem edição elaborada. Estrutura fixa: **20 s de problema, 60 s de
demonstração, 40 s de arquitetura.** Quem vê isto está a decidir se vale a pena abrir o
código — o vídeo não tem de explicar tudo, tem de dar vontade.

**Onde vai parar:** ligado no topo do README, por baixo do GIF. YouTube não listado ou Loom
servem; o que interessa é o link não expirar.

## Antes de gravar

- Os mesmos preparativos do GIF (ver [`README.md`](README.md)): base semeada, janela a
  1280×800, sessão iniciada como `finance@docgrid.local`.
- Abre de antemão os separadores pela ordem do guião: `/review-queue`, `/dashboard`,
  `/exports`, e o editor com `DocumentProcessor.java` aberto no método `process`.
- Microfone a uns 20 cm, numa divisão com cortinas ou alcatifa. Áudio mau estraga um vídeo
  bom; imagem a 1080p chega e sobra.
- Ensaia uma vez inteira antes de gravar. A segunda tentativa costuma ser a boa; à quinta
  perde-se a naturalidade.

## Guião cronometrado

### 0:00 – 0:20 · O problema

**No ecrã:** uma pilha de PDFs de faturas abertos lado a lado, ou o ecrã `/documents` do
DocGrid com os 61 documentos.

> "Uma pequena empresa recebe umas duzentas faturas por mês. Alguém abre cada PDF e copia à
> mão, para o software de contabilidade, o NIF, o número, a data, a base, o IVA e o total.
> São três a quatro minutos por documento — umas dez horas por mês — e os erros de
> digitação acabam na declaração de IVA. O DocGrid transforma esse trabalho de *escrever
> tudo* em *verificar o que o sistema não teve a certeza*."

Não digas o nome de nenhuma tecnologia nestes vinte segundos. Ainda não interessa.

### 0:20 – 1:20 · A demonstração

**No ecrã:** submissão → fila de revisão → ecrã de revisão → aprovação → dashboard.

- **0:20–0:35 — Submeter.** Arrasta um PDF para `/upload`. Enquanto sobe:
  > "O ficheiro vai direto do browser para o S3, com um URL assinado — não passa pela API.
  > O S3 avisa uma fila, e é um worker, do outro lado, que faz a extração."

  Mostra o documento a aparecer na lista e a mudar de estado sozinho.

- **0:35–1:05 — Rever.** Abre um documento da fila com confiança baixa.
  > "Aqui está o documento original à esquerda e o que o sistema leu à direita. Este campo
  > está assinalado porque o motor ficou abaixo dos 85 % de confiança — e quando eu lhe
  > toco, ele mostra-me exatamente onde leu aquilo."

  Clica no campo: **a caixa acende sobre o PDF**. Deixa o silêncio trabalhar um segundo.
  > "Corrijo, confirmo, e aprovo com Ctrl+Enter. O sistema não decidiu por mim em lado
  > nenhum: na dúvida, manda para revisão e diz porquê."

  Mostra rapidamente o duplicado na fila:
  > "Este foi apanhado como duplicado: é a mesma fatura, do mesmo NIF e com o mesmo número,
  > de uma que já tinha sido aprovada — e o sistema liga-o ao original."

- **1:05–1:20 — O resultado.** `/dashboard` e depois `/exports`.
  > "Seis meses de histórico: totais por mês, por categoria, taxa de automação, tempo médio
  > de revisão. E no fim do mês fecha-se o período: o contabilista recebe um CSV com os
  > documentos aprovados, e esses ficam trancados."

### 1:20 – 2:00 · A arquitetura

**No ecrã:** o diagrama do README e, nos últimos segundos, o código.

- **1:20–1:35 — O desenho.** Percorre o diagrama com o cursor.
  > "Spring Boot e Postgres, com a extração atrás de uma interface: em local corre um stub,
  > em AWS é o Textract — quem consome não sabe qual dos dois está do outro lado. Entre o
  > upload e a extração há uma fila, porque extrair demora segundos e depende de um serviço
  > externo."

- **1:35–1:50 — A parte que interessa a quem já trabalhou com filas.**
  > "O SQS entrega pelo menos uma vez, nunca exatamente uma vez. A mesma mensagem chega
  > repetida — e o sistema tem de não se importar. Quem processa reclama o documento numa
  > transação, com a chave do ficheiro como chave primária: só há um vencedor. A segunda
  > entrega encontra o trabalho feito e apaga-se a si própria. O que falha três vezes vai
  > para uma dead-letter queue, que tem ecrã próprio para inspecionar e reprocessar."

  Mostra um segundo o `DocumentProcessor` ou o ecrã da DLQ.

- **1:50–2:00 — Fecho honesto.**
  > "A infraestrutura AWS está escrita em Terraform e validada, mas nunca foi aplicada:
  > não há conta AWS, e isso está documentado. O repositório clona-se e levanta-se com dois
  > comandos, com sessenta documentos de demonstração. O link está aqui em baixo."

## Depois de gravar

- [ ] Dois minutos ou menos. Se passar de 2:15, corta na arquitetura, nunca na demonstração.
- [ ] O áudio ouve-se sem auscultadores e sem picos.
- [ ] Nada de pessoal no ecrã: separadores, notificações, nomes de ficheiros, emails.
- [ ] Legendas automáticas revistas — o vocabulário técnico em português costuma sair mal.
- [ ] O link está no README, por baixo do GIF, e abre sem sessão iniciada.
