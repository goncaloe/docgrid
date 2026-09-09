# Etapa 02 — Upload com URL pré-assinado

## Objetivo
O browser obtém uma autorização do backend e envia o ficheiro direto para o S3.
O backend cria o registo do documento em `UPLOADED`.

## Porque conta
É a primeira coisa que distingue este projeto de um CRUD. Quem revê código nota logo
que o ficheiro não passa pelo servidor.

## Contexto a carregar
`docs/02-ARCHITECTURE.md` (decisões de upload), handoff da etapa 01

## Pré-requisitos
Etapa 01: entidade `Document` e estados a funcionar.

## Âmbito
- `POST /api/documents/upload-url` recebe nome e tipo do ficheiro, devolve URL pré-assinado
  e o id do documento
- Registo criado em `UPLOADED` no momento da emissão, com chave S3 determinística
  (`org/{orgId}/{ano}/{mes}/{uuid}.pdf`)
- Validação: extensões permitidas (pdf, jpg, png), tamanho máximo, tipo MIME
- URL com validade curta (5 minutos)
- Abstração `StorageService` com implementação S3, apontada ao LocalStack em `local`
- `GET /api/documents/{id}/file-url` devolve URL pré-assinado de leitura
- Hash do ficheiro registado para deteção posterior de duplicados binários
- Testes de integração com LocalStack: emitir URL, fazer upload real, confirmar o objeto

## Fora
Processamento do ficheiro depois de subir (etapa 03), interface de upload (etapa 07).

## Decisões desta etapa
- URL pré-assinado simples vs POST com política — recomenda e justifica
- O que fazer quando o registo é criado mas o upload nunca acontece (documentos órfãos):
  desenha a estratégia, implementa a limpeza só se for barata

## Critérios de aceitação
- [ ] Fluxo completo verificável por `curl` + `curl --upload-file`
- [ ] O ficheiro chega ao bucket do LocalStack com a chave esperada
- [ ] Um tipo de ficheiro não permitido é rejeitado com 400 e mensagem clara
- [ ] URL expirado devolve erro do S3, com teste a prová-lo
- [ ] O bucket não é público; sem URL assinado não há acesso

## Esforço estimado
1 sessão · revisão de segurança antes do commit final
