package com.docgrid.export;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Uma linha da exportação: o documento aprovado tal como entra no CSV.
 *
 * <p>É a mesma forma do princípio ao fim — o {@link ExportQueries} lê-a da base de dados e o
 * {@link ExportCsvWriter} escreve-a no ficheiro. Não há tradução pelo meio, e por isso não
 * há a possibilidade de trocar dois montantes ao copiar campo a campo.
 *
 * <p>Os montantes chegam como estão em {@code documents}, sem arredondar: o CSV formata-os,
 * o total da exportação soma-os.
 *
 * @param documentId       o documento de origem, para o marcar como exportado
 * @param issueDate        data da fatura, que é o que decide o período
 * @param invoiceNumber    número da fatura tal como o fornecedor o emitiu
 * @param supplierTaxId    NIF do fornecedor
 * @param supplierName     nome do fornecedor (do campo extraído, da tabela de fornecedores,
 *                         ou o NIF quando nenhum dos dois o tem)
 * @param netAmount        base tributável
 * @param vatRate          taxa de IVA aplicada
 * @param vatAmount        montante de IVA
 * @param totalAmount      total do documento
 * @param category         categoria escolhida na revisão; {@code null} quando não a houve
 * @param currency         moeda do documento
 * @param originalFilename nome do ficheiro submetido, para o contabilista o localizar
 */
record ExportRow(
        UUID documentId,
        LocalDate issueDate,
        String invoiceNumber,
        String supplierTaxId,
        String supplierName,
        BigDecimal netAmount,
        BigDecimal vatRate,
        BigDecimal vatAmount,
        BigDecimal totalAmount,
        String category,
        String currency,
        String originalFilename) {}
