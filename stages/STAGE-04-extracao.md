# Etapa 04 — Extração de dados

## Objetivo
Extrair campos estruturados do documento, cada um com grau de confiança, através de uma
interface com duas implementações: stub local e Textract.

## Porque conta
Mostra que sabes isolar uma dependência externa e cara atrás de uma fronteira. O projeto
inteiro continua a ser desenvolvível e testável sem uma única chamada paga à AWS.

## Contexto a carregar
`docs/01-PRODUCT.md` (campos extraídos), `docs/02-ARCHITECTURE.md`, handoff da etapa 03

## Pré-requisitos
Etapa 03: worker consome a fila e o documento chega a `PROCESSING`.

## Âmbito
- Interface `DocumentExtractor` com um método que recebe a referência do ficheiro e devolve
  campos + confiança + bounding boxes
- `StubExtractor`: lê respostas de ficheiros JSON em `src/test/resources`, ativo em `local`
  e `test`. Deve conseguir simular casos maus (confiança baixa, campo em falta).
- `TextractExtractor`: `AnalyzeExpense` da AWS, ativo no perfil `aws`
- Normalização: converter o que o Textract devolve para o nosso modelo. Datas em vários
  formatos, montantes com vírgula ou ponto, NIF com ou sem prefixo `PT`.
- Persistência em `extracted_fields` com valor, confiança, origem `AI` e bounding box
- Timeout e tratamento de falha do serviço externo (erro transitório → retry)
- Testes: normalização com respostas reais gravadas do Textract, incluindo casos degenerados

## Fora
Decidir se o documento está bom (etapa 05). Aqui só se extrai e guarda.

## Decisões desta etapa
- `AnalyzeExpense` vs `AnalyzeDocument` com queries — discute e recomenda
- Como guardar bounding boxes para o frontend os poder destacar depois
- O que fazer quando o Textract devolve dois candidatos para o mesmo campo

## Critérios de aceitação
- [ ] Todo o pipeline corre localmente sem credenciais AWS, usando o stub
- [ ] Trocar de perfil troca de implementação, sem alterar código de negócio
- [ ] Montantes chegam como `BigDecimal` com escala 2, a partir de formatos variados
- [ ] Cada campo persistido tem confiança e bounding box
- [ ] Existem fixtures de teste para: fatura limpa, foto tremida, campo em falta

## Esforço estimado
1 a 2 sessões · a normalização dá mais trabalho do que parece
