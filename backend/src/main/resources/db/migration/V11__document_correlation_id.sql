-- O id de correlação que atravessou o upload. É o elo entre os logs da API (que o
-- geraram ou aceitaram) e os do worker que mais tarde processa este documento: o
-- worker encontra-o aqui, não em nenhuma mensagem, porque quem produz a mensagem é
-- o S3, e um evento ObjectCreated não carrega atributos nossos — ver
-- docs/adr/0015-id-de-correlacao.md.
--
-- Nulo para documentos anteriores a esta etapa: é normal, não é erro.
-- Sem índice: procura-se nos logs, não em SQL.
alter table documents
    add column correlation_id varchar(64);
