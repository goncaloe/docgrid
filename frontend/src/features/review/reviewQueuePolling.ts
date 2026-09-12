/**
 * Ao contrário da lista de documentos, na fila de revisão não há sinal na página que diga
 * "há algo a mudar": o que se espera é a chegada de trabalho novo, vindo do pipeline. O
 * polling é por isso constante — mas ao ritmo de quem olha para uma fila de trabalho, não
 * de quatro em quatro segundos, que seria custo sem informação. A página e o indicador na
 * navegação usam o mesmo intervalo.
 */
export const REVIEW_QUEUE_POLL_INTERVAL_MS = 15_000;
