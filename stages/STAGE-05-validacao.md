# Etapa 05 — Motor de validação

## Objetivo
Aplicar as regras de negócio aos campos extraídos e decidir: `EXTRACTED` (pronto a aprovar)
ou `NEEDS_REVIEW` (com motivo). Fecha o coração técnico do projeto.

## Porque conta
Aqui está o valor real. O Textract lê; este código decide se o que foi lido faz sentido.
Numa entrevista, é a parte que consegues explicar com orgulho.

## Contexto a carregar
`docs/01-PRODUCT.md` (regras de validação — integral), handoff da etapa 04

## Pré-requisitos
Etapa 04: campos extraídos com confiança persistidos.

## Âmbito
- Interface `ValidationRule`, uma classe por regra, descobertas pelo Spring
- Regras: aritmética, taxa de IVA, NIF (dígito de controlo módulo 11), duplicado,
  confiança mínima, data plausível, limite de aprovação
- Cada resultado guardado em `validation_results` com regra, severidade e mensagem legível
  em português (a mensagem vai aparecer ao utilizador)
- Encaminhamento de estado: tudo verde → `EXTRACTED`; qualquer aviso → `NEEDS_REVIEW`
- Sugestão de categoria a partir do histórico do fornecedor (5 ou mais ocorrências iguais),
  com a justificação guardada
- Atualização da tabela `suppliers` a cada documento aprovado
- Testes unitários exaustivos por regra, com tabelas de casos. O NIF merece atenção especial:
  válidos, inválidos, com prefixo, com espaços, comprimento errado.

## Fora
Interface de revisão (etapa 08). Aqui o resultado verifica-se por API ou base de dados.

## Decisões desta etapa
- Severidade: só `WARNING` (vai a revisão) ou também `ERROR` (rejeita automaticamente)?
  Recomendação: nunca rejeitar automaticamente — na dúvida, humano decide.
- Duplicado é a mesma fatura ou o mesmo ficheiro? Decide o que usar: `NIF + número`,
  hash do ficheiro, ou ambos com mensagens diferentes.
- Regras configuráveis por organização vs fixas em código

## Critérios de aceitação
- [ ] Fatura correta atravessa todo o pipeline sozinha e fica em `EXTRACTED`
- [ ] Fatura com IVA que não bate certo fica em `NEEDS_REVIEW` com mensagem explícita
- [ ] Submeter duas vezes a mesma fatura sinaliza duplicado e aponta para o original
- [ ] NIF inválido é apanhado; o algoritmo tem teste com pelo menos 10 casos
- [ ] Cada regra é testável isoladamente, sem levantar o contexto Spring inteiro
- [ ] Uma regra nova pode ser adicionada sem tocar em código existente (mostra-o com uma)

**Marco:** no fim desta etapa o sistema funciona de ponta a ponta. Grava um vídeo curto
do terminal — vais querer usá-lo mais tarde.

## Esforço estimado
1 sessão
