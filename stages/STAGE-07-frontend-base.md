# Etapa 07 — Frontend base

## Objetivo
Aplicação React que autentica, lista documentos e faz upload com progresso.

## Porque conta
A partir daqui o projeto deixa de ser um conjunto de endpoints e passa a ser algo que se
mostra a alguém.

## Contexto a carregar
`docs/03-CONVENTIONS.md` (secção frontend), OpenAPI da etapa 06, handoff da etapa 06

## Pré-requisitos
Etapa 06: API documentada e a funcionar.

## Âmbito
- Vite + React 18 + TypeScript + Mantine; TanStack Query para dados de servidor
- Lista de documentos com TanStack Table (headless) sobre os componentes do Mantine
- Autenticação: login, armazenamento do token, refresh automático, rotas protegidas por papel
- Layout: navegação lateral, cabeçalho com utilizador, indicador de fila de revisão
- Lista de documentos: tabela com filtros por estado e período, paginação, badges de estado
- Upload: arrastar e largar múltiplos ficheiros, barra de progresso por ficheiro,
  upload direto para o S3 com o URL pré-assinado
- Atualização do estado após upload: polling curto ou WebSocket (decide e justifica)
- Estados vazios, de carregamento e de erro em todos os ecrãs — não deixes ecrãs em branco
- Tipos da API numa única fonte

## Fora
Ecrã de revisão detalhada (etapa 08), dashboard (etapa 09).

## Decisões desta etapa
- Polling vs WebSocket para o progresso de processamento. O polling é honesto e simples;
  o WebSocket impressiona mais. Recomenda com base no esforço.

## Critérios de aceitação
- [ ] Arrastar 5 ficheiros mostra 5 barras de progresso independentes
- [ ] Um documento aparece na lista e muda de estado sozinho, sem recarregar a página
- [ ] Um `EMPLOYEE` não consegue chegar à fila de revisão, nem pelo URL direto
- [ ] Funciona em ecrã de telemóvel
- [ ] Nenhum `any` no código TypeScript

## Esforço estimado
1 a 2 sessões · corre a aplicação e vê o resultado, não te fies só nos testes
