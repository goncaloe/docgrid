# 0001 — Mantine como biblioteca de componentes

**Estado:** aceite · **Data:** 2026-09-09

## Contexto
O frontend precisa de tabela com filtros e paginação, arrastar e largar com progresso por
ficheiro, painel dividido de revisão com campos destacados por grau de confiança, atalhos de
teclado e gráficos de agregação. A stack inicial previa Tailwind, que resolve o estilo mas não
dá componentes: teria de vir acompanhado de uma camada de primitivas (Radix, Base UI) ou de
componentes escritos de raiz. Sendo um projeto de portefólio feito por uma pessoa, o tempo
gasto a construir combobox, modal e datepicker acessíveis é tempo que não se vê no resultado.

## Decisão
**Mantine** para componentes e estilos, com **TanStack Table** (headless) para a lógica da
lista de documentos. Sem Tailwind — um único sistema de estilos, CSS Modules com as variáveis
de tema do Mantine.

Módulos usados por etapa:

| Etapa | Módulos |
|---|---|
| 07 | `@mantine/core`, `@mantine/dropzone`, `@mantine/notifications`, TanStack Table |
| 08 | `@mantine/form`, `@mantine/modals`, `useHotkeys` e `Kbd` de `@mantine/hooks` |
| 09 | `@mantine/charts` (Recharts por baixo) |

O upload continua a ser feito diretamente para o S3 com URL pré-assinado. O `Dropzone` do
Mantine trata apenas de receber os ficheiros; o progresso vem dos eventos de XHR, controlado
por nós.

## Alternativas consideradas

**Tailwind puro** (a escolha inicial). Descartado por não trazer componentes: o custo real
está nos comportamentos acessíveis, não no CSS.

**shadcn/ui**, a combinação mais comum hoje. Descartada por depender de Tailwind, o que
contraria o pedido de partida.

**PrimeReact**. O `DataTable` traz filtros, ordenação e paginação prontos, o que poupava a
integração com o TanStack Table. Descartado por duas razões: a estética empresarial é difícil
de personalizar e num portefólio a captura de ecrã conta, e o `FileUpload` embutido não serve
para upload direto ao S3 — o progresso teria de ser reescrito à mesma.

**MUI**. O nome mais reconhecido e o mais maduro. Descartado pelo Emotion em runtime, pelo
`DataGrid` com filtros ser a versão paga, e por o aspeto Material se identificar de imediato —
o oposto do que se quer numa peça de portefólio.

## Consequências

**Torna fácil:** cobrir as etapas 07 a 09 sem construir primitivas; ter navegação por teclado
e foco visível sem trabalho extra (critérios de aceitação da etapa 08); dar cara própria à
aplicação mexendo só no tema; escrever o destaque de confiança como CSS legível
(`.fieldUncertain`) em vez de uma cadeia de classes utilitárias.

**Torna difícil:** a lista de documentos exige montar o TanStack Table à mão — mais trabalho
inicial do que uma grelha pronta a usar. A comunidade é menor que a do Tailwind, portanto há
menos respostas prontas para casos de canto. Sair do Mantine mais tarde obriga a reescrever
o markup dos componentes, não apenas as folhas de estilo.
