# 0005 — Upload direto para o S3 com URL pré-assinado PUT

**Estado:** aceite · **Data:** 2026-09-09

## Contexto

A etapa 02 põe o ficheiro a subir do browser para o S3 sem passar pelo servidor Java
(decisão de arquitetura em `docs/02-ARCHITECTURE.md`). O backend cria o registo do
documento em `UPLOADED` no momento em que emite a autorização, para que nada suba sem
rasto. Falta decidir a forma da autorização e o que fazer com o tamanho e o hash do
ficheiro, que o servidor não vê.

## Decisão

**URL pré-assinado com `PUT`** (`S3Presigner.presignPutObject`). O cliente faz um `PUT`
para o URL com o corpo do ficheiro; o `Content-Type` vai assinado, portanto o cliente não
o pode trocar. A validade é curta (5 minutos, configurável em `docgrid.upload.url-ttl`).

O tamanho é validado no pedido de autorização a partir do valor que o cliente declara —
um ficheiro grande demais é recusado com `400` antes de se emitir o URL. A chave do S3 é
determinística: `org/{orgId}/{ano}/{mes}/{documentId}.{ext}`, com a extensão derivada do
`Content-Type` e o id a vir do próprio documento (que existe antes do primeiro `flush`).

**O tamanho real e o hash SHA-256 do ficheiro ficam por preencher nesta etapa.** As
colunas `documents.size_bytes` e `documents.file_hash` ficam nulas; o método
`Document.recordUploadedFile` já existe, à espera. Quem os escreve é a etapa 03: o worker
lê o objeto do S3 para o processar e calcula o hash aí, e o evento do S3 traz o tamanho.
Isto desvia-se do âmbito escrito no briefing da etapa 02 ("Hash do ficheiro registado"),
com autorização — sem SQS nem worker, a alternativa era um segundo pedido de confirmação
só para isso.

## Alternativas consideradas

**`POST` com política.** Permite ao S3 impor o tamanho máximo antes de o ficheiro subir,
em vez de se confiar no valor declarado. Descartado por dois motivos: o **AWS SDK para
Java v2 não tem forma de construir o formulário `POST` com política** (só os SDKs de
JavaScript e Python o têm), obrigando a montar a policy em base64 e a assinatura HMAC à
mão; e a única vantagem real — o limite rígido — obtém-se de forma suficiente validando o
tamanho declarado no pedido e podendo a etapa 03 rejeitar o que exceder ao ler o objeto.
Menos peças, e código que quem avalia lê sem decifrar uma assinatura à mão.

**Endpoint de confirmação nesta etapa** (`POST /documents/{id}/upload-complete`), para o
servidor ler o objeto do S3 e calcular tamanho e hash. Adiado com a decisão acima: a
etapa 03 já lê o objeto, e um `headObject` + `GetObject` extra só para o hash seria uma
segunda leitura do mesmo ficheiro. Se a etapa 03 vier a precisar de um sinal explícito do
cliente, cria-se lá.

**Chave com UUID aleatório separado do id do documento.** Tornava a chave impossível de
reconstruir a partir do documento e obrigava a guardar a linha antes de a calcular.

## Consequências

**Torna fácil:** o fluxo completo verifica-se por `curl` + `curl --upload-file`; o
servidor nunca carrega o ficheiro em memória; a chave do S3 lê-se a partir do documento
sem consultar a base de dados.

**Torna difícil:** o tamanho declarado pelo cliente não é o tamanho real até a etapa 03 o
confirmar — um cliente pode declarar 1 KB e enviar 1 GB, e o `PUT` pré-assinado não o
impede (a etapa 03 apanha-o ao ler o objeto). O `file_hash` fica nulo, portanto a regra
do duplicado binário (etapa 05) não funciona até a etapa 03 preencher a coluna.

**Segurança:** a garantia "o bucket não é público" depende de **Block Public Access** e
de não haver política pública no bucket — configuração de S3 que o LocalStack da versão
comunitária não impõe (um `GET` anónimo a um objeto devolve `200`). O que se prova em
testes é o mecanismo da assinatura: um URL expirado e um URL com assinatura adulterada
são recusados com `403` (com `S3_SKIP_SIGNATURE_VALIDATION=0` no LocalStack). O Block
Public Access real fica para o Terraform da etapa 11.
