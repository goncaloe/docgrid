-- Projeção para agregar, à imagem das outras colunas de negócio (net_amount,
-- vat_amount, etc.). A verdade continua em extracted_fields (ADR-0003), com a
-- origem e a confiança.
alter table documents add column category varchar(50);