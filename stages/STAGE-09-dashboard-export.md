# Etapa 09 — Dashboard e exportação

## Objetivo
Visão agregada das despesas e o ficheiro mensal que o contabilista precisa.

## Porque conta
A exportação fecha o ciclo do produto: o sistema deixa de ser uma ilha e passa a alimentar
o processo real da empresa. É o detalhe que faz o projeto parecer pensado por alguém que
percebeu o negócio.

## Contexto a carregar
`docs/01-PRODUCT.md` (personas, contabilista externo), handoff da etapa 08

## Pré-requisitos
Etapa 08: documentos aprovados existem no sistema.

## Âmbito
- Endpoints de agregação: total por mês, por categoria, por fornecedor; IVA total do período
- Dashboard: evolução mensal, repartição por categoria, principais fornecedores,
  indicadores no topo (documentos processados, taxa de automação, tempo médio de revisão)
- **Taxa de automação** é o número mais interessante: percentagem que passou sem revisão humana.
  Destaca-o.
- Exportação mensal em CSV com as colunas que um contabilista espera
- Registo da exportação em `exports`; documentos incluídos passam a `EXPORTED`
- Fechar um período impede alterações retroativas nesses documentos
- Testes: agregações com dados conhecidos; exportação idempotente

## Fora
Integração real com software de contabilidade. Formato SAF-T completo (menciona no README
como evolução possível).

## Decisões desta etapa
- Agregar em SQL ou em Java — SQL, quase de certeza, mas justifica
- Reabrir um período fechado: permitido, proibido, ou permitido com auditoria?

## Critérios de aceitação
- [ ] O dashboard carrega em menos de um segundo com 5000 documentos de teste
- [ ] O CSV abre corretamente no Excel com acentos e vírgulas decimais (testa mesmo)
- [ ] Exportar duas vezes o mesmo mês não duplica documentos
- [ ] A taxa de automação está visível e é calculada corretamente

## Esforço estimado
1 sessão
