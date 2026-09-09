# DocGrid — Domínio e Produto

## O problema

Uma PME recebe entre 100 e 300 faturas por mês. Alguém abre cada PDF e copia à mão
para o software de contabilidade: NIF, número, data, base tributável, IVA, total.
São 3 a 4 minutos por documento e os erros de digitação acabam na declaração de IVA.

O DocGrid reduz esse trabalho de "escrever tudo" para "verificar o que o sistema
não teve a certeza".

## Personas

| Persona | O que faz no sistema | O que vê |
|---|---|---|
| **Funcionário** | Submete despesas próprias (recibo do almoço, combustível) | Só as suas despesas e o estado delas |
| **Assistente financeiro** | Revê a fila de documentos, corrige campos, aprova | Todos os documentos da organização |
| **Gestor** | Aprova despesas acima de um limite configurável | Fila de aprovações + dashboard |
| **Contabilista externo** | Não usa a aplicação. Recebe a exportação mensal | Ficheiro CSV/XML |

O contabilista é fácil de esquecer e é importante: obriga o sistema a exportar dados
para o exterior, que é como as aplicações empresariais realmente vivem.

## Ciclo de vida do documento

```
UPLOADED     → PROCESSING
PROCESSING   → EXTRACTED · NEEDS_REVIEW · FAILED
EXTRACTED    → APPROVED · NEEDS_REVIEW · REJECTED
NEEDS_REVIEW → APPROVED · REJECTED
FAILED       → PROCESSING                      (reprocessamento manual)
APPROVED     → EXPORTED
REJECTED     → (terminal)
EXPORTED     → (terminal)
```

Onze transições em sessenta e quatro pares possíveis. Quem as impõe é o enum
`DocumentStatus`, e quem prova que estão todas certas é `DocumentStatusTest`, que percorre
a matriz inteira. Esta lista e esse enum têm de dizer o mesmo — se divergirem, é aqui que
se decide qual dos dois está errado.

Um documento em `EXTRACTED` pode ser rejeitado diretamente: às vezes está tudo verde e o
documento simplesmente não é uma fatura, e obrigá-lo a passar por `NEEDS_REVIEW` só para
poder ser recusado seria burocracia sem valor.

Reprocessar leva o documento a `PROCESSING` e não a `UPLOADED`: o registo já existe e a
chave do S3 não muda.

| Estado | Significado | Como se sai dele |
|---|---|---|
| `UPLOADED` | Ficheiro no S3, registo criado, ainda não processado | Worker consome a mensagem |
| `PROCESSING` | Extração em curso | Extração termina ou falha |
| `EXTRACTED` | Extraído e validado sem problemas. Sugestão pronta | Humano aprova, manda rever ou rejeita |
| `NEEDS_REVIEW` | Extraído mas algo não bate certo. Motivo registado | Humano corrige e aprova, ou rejeita |
| `FAILED` | Erro técnico (ficheiro corrompido, serviço indisponível) | Reprocessamento manual |
| `APPROVED` | Dados confirmados por um humano. Imutável a partir daqui | Entra na exportação |
| `REJECTED` | Documento inválido, duplicado ou não é uma fatura | Fim |
| `EXPORTED` | Incluído numa exportação mensal fechada | Fim |

**Regra:** transições de estado são registadas em `document_events` com quem, quando e porquê.
O histórico nunca é apagado. Um documento aprovado nunca volta atrás — se estiver errado,
cria-se um documento de correção.

## Campos extraídos

Cada campo carrega valor **e** grau de confiança (0 a 1):

`supplier_name`, `supplier_tax_id` (NIF), `invoice_number`, `issue_date`,
`net_amount`, `vat_amount`, `vat_rate`, `total_amount`, `currency`, `category`.

## Regras de validação

O motor de extração só lê. Não sabe se o que leu faz sentido. As regras são nossas:

| Regra | Verificação | Falha ⇒ |
|---|---|---|
| Aritmética | `net + vat = total`, tolerância de 0,02 € | `NEEDS_REVIEW` |
| Taxa de IVA | `vat / net` aproxima 6%, 13% ou 23% | `NEEDS_REVIEW` |
| NIF válido | Dígito de controlo (módulo 11) | `NEEDS_REVIEW` |
| Duplicado | Já existe `NIF + invoice_number` aprovado | `NEEDS_REVIEW`, com link para o original |
| Confiança mínima | Todos os campos obrigatórios acima de 0.85 | `NEEDS_REVIEW`, campo destacado |
| Data plausível | Entre 24 meses atrás e hoje + 7 dias | `NEEDS_REVIEW` |
| Limite de aprovação | Total acima do limite da organização | Exige aprovação de gestor |

A categoria é **sugerida**, nunca imposta: se este NIF já apareceu 5 ou mais vezes sempre
na mesma categoria, sugere-a com o histórico como justificação.

## Fora de âmbito

Deliberadamente de fora, para o projeto não crescer sem fim:

- Integração real com software de contabilidade (a exportação em ficheiro chega)
- Pagamentos e conciliação bancária
- Multi-tenancy verdadeiro (uma organização por instalação)
- Multi-moeda com conversão (só EUR)
- Aplicação móvel nativa (a web responsiva chega)
