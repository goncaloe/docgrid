-- A etapa 04 acrescenta a geometria dos campos: onde na página cada campo foi lido, para
-- a interface (etapas 07/08) poder sobrepor a caixa ao documento e mandar o utilizador
-- olhar ao sítio certo. O polígono vem em coordenadas normalizadas 0–1 — o próprio
-- Textract devolve-o assim, e normalizado sobrevive a qualquer dimensão de página.

alter table extracted_fields add column page int;
alter table extracted_fields add column bounding_box jsonb;

-- A simetria de ck_extracted_fields_confidence_required (ADR 0003): um campo lido pela
-- máquina sabe sempre onde foi lido; um escrito por uma pessoa já não o sabe.
alter table extracted_fields add constraint ck_extracted_fields_bbox_source check (
    (source = 'AI' and bounding_box is not null and page is not null)
    or (source = 'HUMAN' and bounding_box is null and page is null));

-- O bounding box é sempre um array de pontos, nunca um objeto ou um número — um conteúdo
-- inesperado é um bug a apanhar cedo, na escrita.
alter table extracted_fields add constraint ck_extracted_fields_bbox_json check (
    bounding_box is null or jsonb_typeof(bounding_box) = 'array');
