# Procedimento: arrancar uma etapa

Roteiro neutro, para qualquer agente de código. Quem te invoca indica **o número da etapa**
(duas casas: `00`, `03`, `11`). Onde este documento diz `NN`, usa esse número.

Estás a começar a etapa `NN` do projeto DocGrid numa sessão limpa.
Segue esta ordem, sem saltar passos.

## 1. Carregar contexto

Lê, por esta ordem:

1. `stages/STAGE-NN-*.md` — o briefing desta etapa. É a fonte de verdade do âmbito.
2. O handoff mais recente em `docs/handoffs/` — o que existe já no código.
3. Os documentos listados na secção "Contexto a carregar" do briefing. **Só esses.**
   Não leias toda a pasta `docs/`; o contexto é um recurso finito.
4. O estado real do código: estrutura de pastas, `pom.xml`, migrações existentes.
   Não assumas que o handoff está completo — confirma no disco.

## 2. Verificar pré-requisitos

Confirma que o que a etapa assume já existe. Se faltar alguma coisa, **para e diz** —
não improvises a peça em falta nem alargues o âmbito para a construir.

## 3. Propor um plano

Apresenta, em português:

- **O que vais construir**, em 3 a 5 pontos
- **Os ficheiros** que vais criar ou alterar
- **As decisões** que a etapa exige, com as alternativas e a tua recomendação justificada
- **Os testes** que vais escrever
- **O que fica deliberadamente de fora**, e para que etapa pertence

Marca claramente qualquer ponto onde precises de uma decisão minha.

## 4. Esperar

Não escrevas ficheiros até eu aprovar o plano. Se eu pedir alterações, revê o plano
e volta a apresentá-lo.

Se a tua ferramenta tiver um modo de planeamento que impede escrita em disco, usa-o nesta
fase. Sai dele no passo 5 — o plano aprovado é para ficar em disco.

## 5. Escrever o plano aprovado

Depois de eu aprovar, escreve o plano em `docs/plans/STAGE-NN-plano.md`, seguindo
`docs/PLAN-TEMPLATE.md`, e faz commit: `docs(plano): etapa NN`.

Escreve-o a pensar em quem o vai executar: **outra sessão, sem memória desta conversa, e
possivelmente com um modelo menos capaz do que o teu.** Tudo o que ficou implícito na
discussão e não for para o ficheiro desaparece. Em concreto:

- **Caminhos de ficheiro exatos**, não descrições ("a classe de serviço da validação").
  Quem implementa não leu a árvore de ficheiros contigo.
- **As alternativas postas de lado, e porquê.** Sem isto, quem implementa reabre decisões
  já fechadas — e reabre-as com menos contexto do que quem as fechou.
- **Critérios de aceitação verificáveis**: os comandos que provam que a etapa está feita.
- **Os pontos de paragem**, em "Riscos e pontos de paragem". Vale mais escrever "se acontecer
  X, para e pergunta" do que deixar o silêncio convidar ao palpite.
- **A secção "Desvios durante a execução"** fica no ficheiro, vazia. O plano é escrito para
  ser corrigido contra o disco; deixa-lhe o sítio onde isso se escreve.

## 6. Implementar

A implementação segue `.agents/prompts/implementar-etapa.md` — quer continues nesta sessão,
quer se abra uma sessão limpa para isso. As regras de execução vivem lá; não as repitas aqui.

Qual dos dois caminhos usar depende da etapa, e a tabela está em
`docs/05-WORKFLOW-AGENTES.md`, secção "Como abrir cada etapa".
Na dúvida: etapa de design difícil, fecha a sessão depois do plano commitado; etapa de
iteração visual, onde o plano envelhece a cada ecrã que vês, continua aqui.
