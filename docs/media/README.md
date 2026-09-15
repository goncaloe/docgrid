# O GIF do ecrã de revisão

O GIF é a coisa mais vista do repositório. Quem avalia um portefólio dá-lhe dois a cinco
minutos e a maior parte desse tempo está acima da dobra do README — onde este ficheiro vai
parar. **Vale mais do que todo o texto abaixo dele**, por isso vale os vinte minutos que
custa gravá-lo em condições.

**Ficheiro a produzir:** `docs/media/review.gif` — o README já o referencia.

## Antes de gravar

```bash
npm run down            # base limpa: o percurso abaixo depende dos dados do seed
npm run up              # deixa a correr
npm run seed            # noutro terminal (≈ 15 s)
cd frontend && npm run dev
```

Preparação do ambiente, que se nota no resultado:

- Janela do browser a **1280×800**, sem separadores a mais, sem barra de favoritos, zoom a
  100 %. Modo anónimo evita extensões e o nome de utilizador no canto.
- Entra como `finance@docgrid.local` (password `docgrid-demo`) **antes** de começar a
  gravar: o ecrã de login não interessa a ninguém.
- Abre `/review-queue` e deixa-a carregada. É daqui que o GIF começa.
- Rato em movimentos curtos e deliberados. Nada de procurar botões durante a gravação —
  faz o percurso uma ou duas vezes a seco.

## O percurso, tal como se filma

Cerca de 20 segundos, sem cortes:

1. **(0–3 s) A fila de revisão.** `/review-queue`, com os documentos que esperam revisão.
   Deixa ver as etiquetas de estado e o motivo de cada um por um instante.
2. **(3–5 s) Abrir um documento com um campo incerto.** Escolhe um cuja mensagem fale de
   confiança (*"Confiança insuficiente em…"*). O PDF aparece à esquerda, os campos à direita.
3. **(5–9 s) O momento que interessa.** Clica no campo assinalado: **a caixa acende no sítio
   exato do PDF**, sobre o valor que o motor leu. Deixa-o parado uns segundos — é este
   fotograma que faz a diferença, e é o que ninguém espera ver num projeto de portefólio.
   Passa o rato pela percentagem de confiança, se estiver visível.
4. **(9–13 s) Corrigir.** Escreve o valor certo no campo e sai dele (Tab). O campo passa a
   estar marcado como escrito por uma pessoa e o aviso desaparece.
5. **(13–16 s) Aprovar.** `Ctrl+Enter` (o botão mostra o atalho). Aparece a notificação e a
   aplicação salta sozinha para o documento seguinte da fila.
6. **(16–20 s) Fecho.** Volta a `/review-queue`: a fila tem menos um documento. Fim.

Se quiseres um segundo GIF, o **duplicado** é o melhor candidato: na fila há um documento
marcado como duplicado, com o cartão a ligar ao original — abre-o, mostra o cartão, e
rejeita-o com `Esc`, que abre a janela de motivo.

## Recorte, duração e formato

| | |
| --- | --- |
| Recorte | Só a área da aplicação: fora a barra de endereço, as abas e a barra de tarefas |
| Dimensão final | 1000–1200 px de largura (o GitHub mostra ~880 px; o dobro fica nítido em ecrãs retina) |
| Duração | 18 a 22 segundos. Acima de 25 s, ninguém vê até ao fim |
| Taxa | 12–15 fps chega para movimento de rato e escrita |
| Tamanho | **Abaixo de 5 MB.** O GitHub serve GIFs grandes com lentidão e o README perde o efeito |
| Ciclo | Em ciclo infinito, com uma pausa de ~1 s no último fotograma |

Ferramentas que servem: [ScreenToGif](https://www.screentogif.com/) (Windows, recorta,
corta fotogramas e exporta direto), [LICEcap](https://www.cockos.com/licecap/), ou gravar em
MP4 e converter com `ffmpeg`:

```bash
ffmpeg -i revisao.mp4 -vf "fps=14,scale=1100:-1:flags=lanczos,split[a][b];[a]palettegen[p];[b][p]paletteuse" -loop 0 docs/media/review.gif
```

Se o ficheiro passar dos 5 MB: baixa para 10 fps, corta o início, ou reduz a largura para
900 px — por esta ordem.

## Depois de gravar

- [ ] O ficheiro está em `docs/media/review.gif` e o README mostra-o (vê no GitHub, não só
      no editor).
- [ ] A caixa a acender sobre o PDF vê-se com clareza — é o objetivo do GIF.
- [ ] Não aparece nenhum dado que não seja dos dados de demonstração: sem separadores com o
      teu email, sem notificações do sistema, sem nomes de ficheiros pessoais.
- [ ] Abre bem no telemóvel (o README é lido em telemóvel mais vezes do que se pensa).
