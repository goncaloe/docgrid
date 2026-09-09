# O que faz este projeto impressionar

Quem avalia um portefólio dá-lhe entre dois e cinco minutos. Esta lista é sobre esses minutos.

## O README (o que mais pesa, de longe)

Ordem que funciona:

1. **Uma frase** do que o projeto faz, em linguagem de negócio.
2. **Um GIF** de 20 segundos: arrastar faturas, ver o processamento, corrigir um campo, aprovar.
   Este GIF vale mais que todo o texto abaixo dele.
3. **Link para a demonstração ao vivo** e credenciais de teste.
4. **Diagrama de arquitetura** (Mermaid, renderiza direto no GitHub).
5. **O problema** — 5 linhas sobre a Marta e as 10 horas por mês.
6. **Decisões técnicas** — 4 ou 5 parágrafos curtos: porquê SQS, porquê Postgres e não
   DynamoDB, como se garante idempotência, como se lida com baixa confiança. Link para os ADRs.
7. **Como correr localmente** — `git clone`, `npm run up`, e funciona. Testa isto numa máquina limpa.
8. **Testes e cobertura**, com badge.

O que **não** deve estar lá: lista de tecnologias sem contexto, screenshots de código,
lista de funcionalidades futuras que nunca vais fazer.

## Dados de demonstração

Um comando `npm run seed` que cria 60 documentos com histórico realista: aprovados de meses
anteriores, alguns em revisão com motivos diferentes (IVA que não bate, NIF ilegível,
duplicado detetado), um rejeitado. Sem isto, quem abre a demonstração vê tabelas vazias
e fecha o separador.

Inclui um duplicado real, para que o revisor consiga ver o sistema a apanhá-lo.

## Os três detalhes que separam do resto

**A DLQ.** Poucos projetos de portefólio têm dead-letter queue com um ecrã para inspecionar e
reprocessar mensagens falhadas. Quem já trabalhou com filas reconhece isto imediatamente.

**Os graus de confiança visíveis na interface.** Campos com confiança baixa a amarelo, com
o valor em percentagem ao passar o rato. Mostra que percebes que sistemas de IA são
probabilísticos e que o desenho tem de lidar com isso.

**A validação do NIF português.** É meia dúzia de linhas mas mostra atenção ao domínio real.
Quem revê o código nota.

## O ecrã de revisão

É a tela que vais mostrar. Merece atenção desproporcionada:

- Documento original à esquerda, campos à direita, alinhados
- Campos incertos com fundo amarelo e o motivo em texto
- Ao focar um campo, destacar a zona correspondente na imagem (bounding box do Textract)
- Atalhos de teclado: Tab entre campos, Enter para aprovar, Esc para rejeitar
- Aprovar em menos de dois segundos

## Vídeo

Dois minutos, sem edição elaborada, com voz. Estrutura: o problema em 20 segundos,
demonstração em 60, arquitetura em 40. Põe no topo do README.

## Antes de partilhares

- [ ] `git clone` numa pasta nova, `npm run up`, funciona à primeira
- [ ] Não há credenciais no histórico do git (corre `gitleaks` ou `truffleHog`)
- [ ] A demonstração ao vivo está de pé e os dados de teste estão lá
- [ ] O README abre bem em telemóvel
- [ ] Os testes passam no CI e o badge é verde
- [ ] Consegues explicar qualquer ficheiro do repositório sem hesitar
- [ ] Orçamento AWS configurado com alerta, para não teres surpresas
