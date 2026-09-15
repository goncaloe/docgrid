package com.docgrid.demo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

import com.docgrid.document.ExtractedFieldName;
import com.docgrid.document.InvoiceFields;
import com.docgrid.extraction.FieldGeometry;

/**
 * As faturas que o seed submete, geradas uma vez e sempre iguais.
 *
 * <p>Determinístico por desenho ({@link Random} com semente fixa): a demonstração é a
 * mesma em todas as máquinas, o GIF gravado hoje continua a bater certo com o que se vê
 * amanhã, e um problema reproduz-se sem "no meu computador saiu outra coisa". A única
 * coisa que varia é a data de referência — as faturas têm de cair nos últimos seis meses
 * de <em>quem corre o seed</em>, senão a regra da data plausível manda-as todas para
 * revisão e o dashboard fica com uma barra só.
 *
 * <p>Os casos de negócio ({@link DemoCase}) não são aleatórios: estão plantados em
 * posições fixas, para a fila de revisão ter sempre os mesmos exemplos. Com menos faturas
 * do que 60 — o teste de integração do seed corre com quatro — aplicam-se só os que
 * couberem.
 *
 * <p>Os fornecedores são inventados. O NIF de cada um é gerado com o dígito de controlo
 * correto (módulo 11, a mesma fórmula da {@code TaxIdRule}), porque um NIF inválido fazia
 * disparar uma regra que nada tem a ver com o caso que a fatura quer mostrar.
 */
public class DemoInvoiceCatalog {

    /** A semente. Muda-a e a demonstração inteira muda — não há razão para o fazer. */
    private static final long SEED = 20260915L;

    /** Quem emite as faturas: nome, categoria habitual e taxa de IVA do seu negócio. */
    private static final List<Supplier> SUPPLIERS = List.of(
            new Supplier("Papelaria Central, Lda.", "Material de escritório", 23),
            new Supplier("Eletro Litoral, S.A.", "Eletricidade", 23),
            new Supplier("NetLusa Comunicações, S.A.", "Comunicações", 23),
            new Supplier("Cantina do Zé, Lda.", "Refeições", 13),
            new Supplier("TecnoSol Informática, Lda.", "Software", 23),
            new Supplier("Transportes Vale do Sado, Lda.", "Transportes", 23),
            new Supplier("Consultores Ribeiro e Associados", "Consultoria", 23),
            new Supplier("Gráfica Aurora, Lda.", "Impressão", 23),
            new Supplier("Móveis do Douro, S.A.", "Equipamento", 23),
            new Supplier("Limpezas Atlântico, Unipessoal Lda.", "Limpeza", 23),
            new Supplier("Seguros Bandeira, S.A.", "Seguros", 23),
            new Supplier("Auto Peças do Minho, Lda.", "Manutenção", 23),
            new Supplier("Café Rossio, Lda.", "Refeições", 13),
            new Supplier("Águas do Vale, S.A.", "Água", 6),
            new Supplier("Formação Sigma, Lda.", "Formação", 23),
            new Supplier("Hotel Ribeira Azul, S.A.", "Deslocações", 6),
            new Supplier("Imobiliária Praça Nova, Lda.", "Rendas", 23),
            new Supplier("Combustíveis do Tejo, S.A.", "Combustível", 23),
            new Supplier("Clínica Saúde Mais, Lda.", "Saúde ocupacional", 6),
            new Supplier("Estúdio Lumen, Lda.", "Marketing", 23));

    /**
     * Onde cada caso é plantado, por posição na lista. As posições são fixas para a
     * demonstração ser sempre a mesma; a cópia do duplicado vem propositadamente muito
     * depois do original, para o histórico parecer o que é — a mesma fatura submetida
     * outra vez, semanas depois.
     */
    /** As duas faturas a que falta um campo: a uma a data de emissão, à outra o número. */
    private static final int MISSING_ISSUE_DATE_AT = 21;

    private static final int MISSING_INVOICE_NUMBER_AT = 47;

    private static final Map<Integer, DemoCase> PLANTED_CASES = plantedCases();

    /** Onde cada campo é lido na página, em coordenadas normalizadas 0–1. */
    private static final Map<ExtractedFieldName, FieldGeometry> GEOMETRY = geometry();

    private final List<DemoInvoice> invoices;
    private final Map<String, DemoInvoice> bySlug;

    /**
     * @param count quantas faturas gerar; 60 é o valor da demonstração
     * @param today a data a partir da qual as faturas recuam até seis meses
     */
    public DemoInvoiceCatalog(int count, LocalDate today) {
        if (count < 1) {
            throw new IllegalArgumentException("O catálogo tem de ter pelo menos uma fatura, e não " + count);
        }
        this.invoices = List.copyOf(generate(count, today));
        Map<String, DemoInvoice> index = new LinkedHashMap<>();
        invoices.forEach(invoice -> index.put(invoice.slug(), invoice));
        this.bySlug = Map.copyOf(index);
    }

    /** As faturas, pela ordem por que o seed as submete. */
    public List<DemoInvoice> invoices() {
        return invoices;
    }

    /** A fatura que o marcador de um PDF identifica. */
    public Optional<DemoInvoice> findBySlug(String slug) {
        return Optional.ofNullable(bySlug.get(slug));
    }

    /** A primeira do catálogo: o que o extractor devolve a um PDF sem marcador. */
    public DemoInvoice first() {
        return invoices.get(0);
    }

    private static List<DemoInvoice> generate(int count, LocalDate today) {
        Random random = new Random(SEED);
        Map<String, Integer> sequenceBySupplier = new LinkedHashMap<>();
        List<DemoInvoice> generated = new ArrayList<>(count);
        DemoInvoice duplicateOriginal = null;

        for (int index = 0; index < count; index++) {
            DemoCase demoCase = PLANTED_CASES.getOrDefault(index, DemoCase.CLEAN);
            Supplier supplier = SUPPLIERS.get(random.nextInt(SUPPLIERS.size()));
            String slug = "inv-%03d".formatted(index + 1);

            if (demoCase == DemoCase.DUPLICATE_COPY && duplicateOriginal != null) {
                generated.add(copyOf(duplicateOriginal, slug, today, random));
                continue;
            }

            LocalDate issueDate = today.minusDays(7 + random.nextInt(175));
            String invoiceNumber = nextInvoiceNumber(sequenceBySupplier, supplier, issueDate);
            String taxId = demoCase == DemoCase.INVALID_TAX_ID ? breakCheckDigit(supplier.taxId()) : supplier.taxId();

            BigDecimal net = netAmount(random);
            BigDecimal vatRate = BigDecimal.valueOf(supplier.vatRate()).setScale(2, RoundingMode.UNNECESSARY);
            BigDecimal vat = net.multiply(vatRate).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            BigDecimal total = net.add(vat);
            if (demoCase == DemoCase.BAD_ARITHMETIC) {
                // Um erro de leitura plausível: uma dezena a mais no total.
                total = total.add(new BigDecimal("10.00"));
            }

            boolean missingIssueDate = index == MISSING_ISSUE_DATE_AT;
            boolean missingInvoiceNumber = index == MISSING_INVOICE_NUMBER_AT;

            InvoiceFields fields = new InvoiceFields(
                    supplier.name(),
                    taxId,
                    missingInvoiceNumber ? null : invoiceNumber,
                    missingIssueDate ? null : issueDate,
                    net,
                    vat,
                    vatRate,
                    total);

            Map<ExtractedFieldName, BigDecimal> confidences = confidences(random, fields, demoCase);
            DemoInvoice invoice = new DemoInvoice(
                    slug, fields, confidences, geometriesFor(confidences), supplier.category(), demoCase);
            generated.add(invoice);
            if (demoCase == DemoCase.DUPLICATE_ORIGINAL) {
                duplicateOriginal = invoice;
            }
        }
        return generated;
    }

    /**
     * A mesma fatura outra vez: o mesmo fornecedor, o mesmo NIF e o mesmo número. Só a
     * data de emissão e as confianças mudam — foi digitalizada noutro dia, e é assim que
     * um duplicado verdadeiro chega a um sistema destes.
     */
    private static DemoInvoice copyOf(DemoInvoice original, String slug, LocalDate today, Random random) {
        InvoiceFields source = original.fields();
        InvoiceFields fields = new InvoiceFields(
                source.supplierName(),
                source.supplierTaxId(),
                source.invoiceNumber(),
                today.minusDays(3 + random.nextInt(10)),
                source.netAmount(),
                source.vatAmount(),
                source.vatRate(),
                source.totalAmount());
        Map<ExtractedFieldName, BigDecimal> confidences = confidences(random, fields, DemoCase.DUPLICATE_COPY);
        return new DemoInvoice(
                slug,
                fields,
                confidences,
                geometriesFor(confidences),
                original.suggestedCategory(),
                DemoCase.DUPLICATE_COPY);
    }

    /**
     * A confiança de cada campo lido. Alta por omissão — o motor está a ler um PDF gerado,
     * não uma fotografia tremida; no caso {@link DemoCase#LOW_CONFIDENCE}, um campo cai
     * abaixo do limiar de 0,85 e é esse que o revisor vai ver assinalado.
     */
    private static Map<ExtractedFieldName, BigDecimal> confidences(
            Random random, InvoiceFields fields, DemoCase demoCase) {
        Map<ExtractedFieldName, BigDecimal> confidences = new EnumMap<>(ExtractedFieldName.class);
        for (ExtractedFieldName field : ExtractedFieldName.values()) {
            if (valueOf(fields, field) != null) {
                confidences.put(field, confidence(random, 0.88, 0.99));
            }
        }
        if (demoCase == DemoCase.LOW_CONFIDENCE) {
            confidences.put(lowConfidenceField(random), confidence(random, 0.55, 0.84));
        }
        return confidences;
    }

    /** O campo que a extração leu mal. Sempre um que o revisor consegue confirmar a olho. */
    private static ExtractedFieldName lowConfidenceField(Random random) {
        List<ExtractedFieldName> candidates = List.of(
                ExtractedFieldName.SUPPLIER_TAX_ID,
                ExtractedFieldName.INVOICE_NUMBER,
                ExtractedFieldName.TOTAL_AMOUNT,
                ExtractedFieldName.ISSUE_DATE);
        return candidates.get(random.nextInt(candidates.size()));
    }

    private static BigDecimal confidence(Random random, double min, double max) {
        double value = min + random.nextDouble() * (max - min);
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    /** Só os campos que foram lidos têm geometria: é o contrato do {@code ExtractionResult}. */
    private static Map<ExtractedFieldName, FieldGeometry> geometriesFor(
            Map<ExtractedFieldName, BigDecimal> confidences) {
        Map<ExtractedFieldName, FieldGeometry> geometries = new EnumMap<>(ExtractedFieldName.class);
        confidences.keySet().forEach(field -> geometries.put(field, GEOMETRY.get(field)));
        return geometries;
    }

    /**
     * A base tributável. A maioria abaixo do limite de aprovação da organização (1000 €) e
     * uma minoria acima — é esse punhado que só um gestor pode aprovar, e a demonstração
     * precisa de o mostrar.
     */
    private static BigDecimal netAmount(Random random) {
        int draw = random.nextInt(100);
        double value;
        if (draw < 70) {
            value = 25 + random.nextDouble() * 375;
        } else if (draw < 90) {
            value = 400 + random.nextDouble() * 550;
        } else {
            value = 1000 + random.nextDouble() * 2000;
        }
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    private static String nextInvoiceNumber(
            Map<String, Integer> sequenceBySupplier, Supplier supplier, LocalDate issueDate) {
        int sequence = sequenceBySupplier.merge(supplier.name(), 1, Integer::sum);
        return "FT %d/%04d".formatted(issueDate.getYear(), sequence);
    }

    private static String valueOf(InvoiceFields fields, ExtractedFieldName field) {
        return switch (field) {
            case SUPPLIER_NAME -> fields.supplierName();
            case SUPPLIER_TAX_ID -> fields.supplierTaxId();
            case INVOICE_NUMBER -> fields.invoiceNumber();
            case ISSUE_DATE ->
                fields.issueDate() == null ? null : fields.issueDate().toString();
            case NET_AMOUNT ->
                fields.netAmount() == null ? null : fields.netAmount().toPlainString();
            case VAT_AMOUNT ->
                fields.vatAmount() == null ? null : fields.vatAmount().toPlainString();
            case VAT_RATE -> fields.vatRate() == null ? null : fields.vatRate().toPlainString();
            case TOTAL_AMOUNT ->
                fields.totalAmount() == null ? null : fields.totalAmount().toPlainString();
            case CURRENCY, CATEGORY -> null;
        };
    }

    /**
     * O NIF de um fornecedor: oito dígitos derivados da sua posição e o dígito de controlo
     * calculado por módulo 11 — a fórmula da {@code TaxIdRule}, replicada aqui porque
     * aquela classe é interna ao pacote da validação.
     */
    private static String taxId(int supplierIndex) {
        String eightDigits = String.valueOf(50_100_000 + supplierIndex * 13_791);
        return eightDigits + checkDigit(eightDigits);
    }

    private static char checkDigit(String eightDigits) {
        int sum = 0;
        for (int i = 0; i < 8; i++) {
            sum += (eightDigits.charAt(i) - '0') * (9 - i);
        }
        int remainder = sum % 11;
        return (char) ('0' + (remainder < 2 ? 0 : 11 - remainder));
    }

    /** Estraga o dígito de controlo sem mexer nos outros oito: o NIF fica com cara de NIF. */
    private static String breakCheckDigit(String taxId) {
        char wrong = (char) ('0' + ((taxId.charAt(8) - '0' + 1) % 10));
        return taxId.substring(0, 8) + wrong;
    }

    private static Map<Integer, DemoCase> plantedCases() {
        Map<Integer, DemoCase> cases = new LinkedHashMap<>();
        for (int index : new int[] {3, 7, 12, 19, 26, 33, 41, 52}) {
            cases.put(index, DemoCase.LOW_CONFIDENCE);
        }
        for (int index : new int[] {9, 24, 45}) {
            cases.put(index, DemoCase.BAD_ARITHMETIC);
        }
        for (int index : new int[] {15, 38}) {
            cases.put(index, DemoCase.INVALID_TAX_ID);
        }
        cases.put(MISSING_ISSUE_DATE_AT, DemoCase.MISSING_FIELD);
        cases.put(MISSING_INVOICE_NUMBER_AT, DemoCase.MISSING_FIELD);
        cases.put(5, DemoCase.DUPLICATE_ORIGINAL);
        cases.put(31, DemoCase.DUPLICATE_COPY);
        return Map.copyOf(cases);
    }

    /**
     * O sítio de cada campo na página, igual em todas as faturas — é o mesmo modelo de
     * fatura. O {@code InvoicePdfWriter} desenha cada campo dentro do seu polígono, e é
     * por isso que a caixa acende no sítio certo no ecrã de revisão.
     */
    private static Map<ExtractedFieldName, FieldGeometry> geometry() {
        Map<ExtractedFieldName, FieldGeometry> geometry = new EnumMap<>(ExtractedFieldName.class);
        geometry.put(ExtractedFieldName.SUPPLIER_NAME, box(0.06, 0.06, 0.62, 0.11));
        geometry.put(ExtractedFieldName.SUPPLIER_TAX_ID, box(0.06, 0.13, 0.38, 0.17));
        geometry.put(ExtractedFieldName.INVOICE_NUMBER, box(0.60, 0.13, 0.94, 0.17));
        geometry.put(ExtractedFieldName.ISSUE_DATE, box(0.60, 0.19, 0.94, 0.23));
        geometry.put(ExtractedFieldName.NET_AMOUNT, box(0.60, 0.62, 0.94, 0.66));
        geometry.put(ExtractedFieldName.VAT_RATE, box(0.34, 0.68, 0.56, 0.72));
        geometry.put(ExtractedFieldName.VAT_AMOUNT, box(0.60, 0.68, 0.94, 0.72));
        geometry.put(ExtractedFieldName.TOTAL_AMOUNT, box(0.60, 0.75, 0.94, 0.80));
        return Map.copyOf(geometry);
    }

    private static FieldGeometry box(double left, double top, double right, double bottom) {
        return new FieldGeometry(
                1,
                List.of(
                        new FieldGeometry.Point(left, top),
                        new FieldGeometry.Point(right, top),
                        new FieldGeometry.Point(right, bottom),
                        new FieldGeometry.Point(left, bottom)));
    }

    /** Um fornecedor inventado, com a categoria e a taxa de IVA do seu negócio. */
    private record Supplier(String name, String category, int vatRate) {

        String taxId() {
            return DemoInvoiceCatalog.taxId(SUPPLIERS.indexOf(this));
        }
    }
}
