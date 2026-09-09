# Etapa 12 — Vitrine

## Objetivo
Transformar o repositório em algo que convence alguém em três minutos.

## Porque conta
Um projeto excelente mal apresentado avalia-se como um projeto medíocre. Esta etapa tem
o melhor retorno por hora de todo o roteiro. **Não a saltes.**

## Contexto a carregar
`docs/06-DEMO-CHECKLIST.md` (integral), `docs/01-PRODUCT.md`, todos os ADRs

## Pré-requisitos
Aplicação a funcionar. Idealmente com deploy feito, mas dá para fazer sem.

## Âmbito
- README novo, seguindo a ordem do checklist de demonstração
- Diagrama de arquitetura em Mermaid, dentro do README
- Diagrama do ciclo de vida do documento
- `npm run seed`: 60 documentos com histórico realista, incluindo casos de revisão variados
  e um duplicado detetável
- GIF do ecrã de revisão, curto e recortado
- Vídeo de 2 minutos com voz: problema, demonstração, arquitetura
- Rever e completar os ADRs; devem ser 5 a 8, coerentes entre si
- `CONTRIBUTING.md` curto e `LICENSE`
- Limpeza: remover TODOs, código morto, ficheiros de rascunho
- Verificação final numa máquina limpa: clonar, `npm run up`, funcionar

## Fora
Funcionalidades novas. Se descobrires uma falha, decide friamente se vale a pena — na maioria
dos casos, documenta-a como limitação conhecida e segue.

## Critérios de aceitação
- [ ] Alguém que não conhece o projeto percebe o que faz nos primeiros 30 segundos do README
- [ ] O GIF está acima da dobra
- [ ] Os ADRs explicam as decisões sem que seja preciso ler código
- [ ] `git clone` numa pasta nova + `npm run up` funciona à primeira
- [ ] A demonstração ao vivo tem dados e não está vazia
- [ ] Consegues explicar qualquer ficheiro do repositório sem hesitar

## Esforço estimado
1 sessão · escrita, não código

## Depois disto
Escreve um artigo curto sobre uma decisão técnica do projeto (idempotência com SQS é um
bom tema) e publica-o. Liga-o no README. Duplica o alcance do projeto por duas horas de trabalho.
