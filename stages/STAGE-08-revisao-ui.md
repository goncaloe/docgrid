# Etapa 08 — Ecrã de revisão

## Objetivo
O documento original lado a lado com os campos extraídos, com a incerteza visível e
correção rápida por teclado.

## Porque conta
**É a tela que vais mostrar em entrevistas.** Merece mais atenção que qualquer outra parte
do frontend. É aqui que se vê que percebeste que a saída de um modelo é probabilística
e que o desenho tem de refletir isso.

## Contexto a carregar
`docs/06-DEMO-CHECKLIST.md` (secção do ecrã de revisão), `docs/01-PRODUCT.md` (validação),
handoff da etapa 07

## Pré-requisitos
Etapa 07: aplicação a funcionar; etapa 04: bounding boxes guardados.

## Âmbito
- Painel dividido: pré-visualização do documento à esquerda, formulário à direita
- Campos com confiança abaixo do limiar destacados a amarelo, com a percentagem visível
- Mensagens de validação junto ao campo em causa, em português claro
  ("O IVA declarado não corresponde a 23% da base tributável")
- Ao focar um campo, destacar a região correspondente na imagem (bounding box)
- Duplicado detetado mostra um cartão com link para o documento original
- Atalhos: Tab entre campos, Ctrl+Enter aprova, Esc rejeita com motivo
- Ao aprovar, avançar automaticamente para o documento seguinte da fila
- Contador de progresso da fila ("3 de 12")

## Fora
Dashboard, exportação.

## Decisões desta etapa
- Renderizar PDF no browser: `react-pdf` vs imagem gerada no servidor. Fotos de telemóvel
  já são imagens; PDFs precisam de decisão.
- Guardar rascunho de correções ou só ao aprovar

## Critérios de aceitação
- [ ] Rever e aprovar um documento demora menos de 5 segundos com rato
- [ ] Todo o fluxo é possível só com teclado
- [ ] Campos incertos são visivelmente distintos dos que estão bem
- [ ] Clicar num campo mostra onde ele está no documento
- [ ] Um duplicado mostra o original e não deixa aprovar sem confirmação explícita
- [ ] Grava um GIF deste ecrã — é o GIF do README

## Esforço estimado
1 a 2 sessões · itera visualmente, não aceites o primeiro resultado
