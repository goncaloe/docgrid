# DocGrid

Plataforma de processamento automático de faturas e despesas. Projeto de portefólio,
não é produto comercial. Backend Java/Spring Boot, frontend React, infraestrutura AWS.

> **Este ficheiro é a fonte única de instruções para qualquer agente de código neste
> repositório.** Os ficheiros específicos de cada ferramenta (`CLAUDE.md` e outros que
> venham a existir) importam este e contêm apenas o que é próprio dessa ferramenta.
> Uma regra muda aqui, e em mais lado nenhum.

## O que o sistema faz

O utilizador submete um PDF ou foto de uma fatura. O sistema extrai os campos
(fornecedor, NIF, número, data, base, IVA, total), valida-os contra regras de negócio,
e classifica o documento como pronto a aprovar ou a precisar de revisão humana.
Documentos com baixa confiança ou que falham validação vão para uma fila de revisão manual.

**O sistema não substitui o humano — reduz-lhe o trabalho e diz-lhe onde olhar.**
Esta frase decide muitas dúvidas de design. Na dúvida entre "adivinhar" e "sinalizar
para revisão", sinaliza sempre.

## Contexto de trabalho

- O autor é um programador a construir portefólio. Explica decisões, não despejes código.
- Domínio fiscal português: NIF com dígito de controlo, IVA a 6%, 13% e 23%.
- Custo AWS importa. Desenvolvimento local com LocalStack; nada de recursos pagos por defeito.

## Documentação de referência

Lê sob demanda, não tudo de uma vez:

- `docs/01-PRODUCT.md` — domínio, personas, estados, regras de negócio
- `docs/02-ARCHITECTURE.md` — arquitetura e decisões técnicas
- `docs/03-CONVENTIONS.md` — convenções de código e testes
- `docs/04-ROADMAP.md` — etapas do projeto
- `docs/05-WORKFLOW-AGENTES.md` — como se conduz uma sessão de trabalho
- `stages/STAGE-XX-*.md` — briefing da etapa atual
- `docs/handoffs/` — o que ficou feito em etapas anteriores
- `docs/plans/` — o plano aprovado da etapa, quando o há

## Procedimentos

Roteiros de tarefa, escritos de forma neutra para qualquer agente os poder seguir:

- `.agents/prompts/arrancar-etapa.md` — abrir uma etapa: carregar contexto, propor o plano
  e escrevê-lo em `docs/plans/` depois de aprovado
- `.agents/prompts/implementar-etapa.md` — executar um plano aprovado, mesmo sem a conversa
  que o produziu
- `.agents/prompts/handoff.md` — fechar uma etapa: escrever o relatório em `docs/handoffs/`

Se a ferramenta que estás a usar tiver comandos próprios (slash commands, skills), eles são
apenas invólucros finos destes ficheiros. O procedimento vive aqui.

## Regras permanentes

1. **Uma etapa de cada vez.** Não implementes o que pertence a etapas futuras, mesmo que
   pareça trivial. Se algo em falta bloquear a etapa atual, diz e pergunta.
2. **Plano antes de código** em qualquer alteração que toque em mais de dois ficheiros.
3. **Testes junto com a funcionalidade**, não numa etapa "de testes" no fim.
4. **Sem segredos no repositório.** Credenciais por variável de ambiente, sempre.
5. **Português nas explicações, no README e commits. Inglês no código**, nomes de variáveis, classes, nomes de tabelas e campos do postgres.
6. **Confiança explícita.** Qualquer campo extraído carrega o seu grau de confiança até
   à interface. Nunca descartes essa informação pelo caminho.
7. Quando uma decisão tiver alternativas defensáveis, escreve um ADR curto em
   `docs/adr/NNNN-titulo.md` em vez de a enterrares no código.
8. **Mensagens de commit limpas.** Sem co-autoria de ferramentas nem nome de agente,
   sem link de sessão, sem número de issue. Só o que descreve a alteração.
9. **Qualidade antes de esforço.** Ao decidir tecnicamente, não peses o trabalho que dá
   implementar. Escolhe o que é mais simples de perceber, robusto, escalável e sustentável
   a prazo. (Isto é sobre esforço de desenvolvimento; o custo de AWS continua a contar.)
10. **Nada de paralelismo em massa sem autorização.** Antes de lançares vários subagentes
    ou tarefas em paralelo, workflows dinâmicos ou revisões multi-agente, explica os
    compromissos e espera por aprovação explícita.

## Stack fixa

Java 21, Spring Boot 3.x, Maven, PostgreSQL 16, Flyway, Testcontainers, JUnit 5, Spotless.
React 18 + TypeScript + Vite, TanStack Query, Mantine (componentes e estilos),
@mantine/charts (gráficos do dashboard, alineado co @mantine/core), TanStack Table.
AWS: S3, SQS, Textract, RDS, EC2 t4g.micro, CloudWatch. A elección da computación
(EC2 contra ECS Fargate) e o desenho da rede estão justificados en
`docs/adr/0017-computacion-e-rede-en-aws.md`.
LocalStack + Docker Compose para desenvolvimento local (imagem do LocalStack na linha 4.x:
as mais recentes exigem token de licença).

Não troques nada disto sem me perguntares.

## Comandos do projeto

Usa sempre estes, não os comandos por baixo deles:

```
npm run up     # sobe Postgres e LocalStack, e arranca a aplicação
npm run infra  # só a infraestrutura, para correr a aplicação no IDE
npm test       # testes backend
npm run lint   # formatação e análise estática
```

Não há `Makefile`: ver `docs/adr/0002-ambiente-local-e-comandos.md`.
