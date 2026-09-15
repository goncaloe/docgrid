package com.docgrid.demo;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.Charset;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import com.docgrid.document.ExtractedFieldName;
import com.docgrid.document.InvoiceFields;
import com.docgrid.extraction.FieldGeometry;

/**
 * Desenha o PDF de uma fatura de demonstração, à mão, sem nenhuma biblioteca.
 *
 * <p>Uma página A4, PDF 1.4, sem compressão, com as duas Helvetica que qualquer leitor de
 * PDF traz consigo. São umas dezenas de linhas de operadores de texto; a alternativa era
 * juntar 5 MB de dependência ao {@code pom.xml} — e à stack fixa do {@code AGENTS.md} —
 * só para produzir dados de demonstração.
 *
 * <p>A razão de fundo é outra, e é o que faz o ecrã de revisão impressionar: <strong>cada
 * campo é desenhado dentro do polígono da sua {@link FieldGeometry}</strong>. A caixa que
 * o frontend sobrepõe ao PDF é a mesma que serviu de régua a quem o escreveu, por isso
 * acende exatamente sobre o valor. Uma biblioteca compunha a página à maneira dela e a
 * geometria teria de ser adivinhada a seguir.
 *
 * <p>A segunda linha do ficheiro é um comentário — {@code %DocGridDemo: <slug>} — que os
 * leitores de PDF ignoram e que o {@code DemoExtractor} usa para saber que fatura está a
 * ler. É assim que o mesmo documento que subiu para o S3 volta a encontrar os seus valores
 * do outro lado do pipeline, sem se tocar em código de produção.
 */
public final class InvoicePdfWriter {

    /** O comentário que liga um PDF à sua entrada no catálogo. */
    public static final String MARKER_PREFIX = "%DocGridDemo: ";

    /** A codificação das fontes base do PDF; cobre os acentos do português. */
    private static final Charset WIN_ANSI = Charset.forName("windows-1252");

    private static final DateTimeFormatter PORTUGUESE_DATE = DateTimeFormatter.ofPattern("dd/MM/uuuu");

    /** A4 em pontos tipográficos, a unidade do PDF. */
    private static final double PAGE_WIDTH = 595;

    private static final double PAGE_HEIGHT = 842;

    /** Só o cabeçalho precisa dos primeiros bytes; ler mais é desperdício. */
    private static final int MARKER_SEARCH_LIMIT = 200;

    private InvoicePdfWriter() {}

    /** O PDF desta fatura, pronto a subir para o S3. */
    public static byte[] write(DemoInvoice invoice) {
        String content = pageContent(invoice);
        return document(invoice.slug(), content.getBytes(WIN_ANSI));
    }

    /**
     * O slug escrito no marcador deste PDF, se lá estiver.
     *
     * <p>Vive aqui, ao lado de quem o escreve, para o formato do marcador existir num
     * sítio só: quem o lê e quem o escreve não podem divergir.
     */
    public static Optional<String> markerOf(byte[] pdf) {
        String head = new String(pdf, 0, Math.min(pdf.length, MARKER_SEARCH_LIMIT), WIN_ANSI);
        int start = head.indexOf(MARKER_PREFIX);
        if (start < 0) {
            return Optional.empty();
        }
        int from = start + MARKER_PREFIX.length();
        int end = head.indexOf('\n', from);
        String slug = (end < 0 ? head.substring(from) : head.substring(from, end)).trim();
        return slug.isEmpty() ? Optional.empty() : Optional.of(slug);
    }

    /**
     * O corpo da página: primeiro o que é fixo em qualquer fatura (rótulos, filetes, o
     * cliente), depois cada valor extraído dentro da sua caixa.
     */
    private static String pageContent(DemoInvoice invoice) {
        InvoiceFields fields = invoice.fields();
        Map<ExtractedFieldName, FieldGeometry> geometries = invoice.geometries();
        StringBuilder page = new StringBuilder();

        rule(page, 0.07, 0.093, 0.93, 1.2);
        rule(page, 0.07, 0.330, 0.93, 1.0);
        rule(page, 0.07, 0.372, 0.93, 0.6);
        rule(page, 0.07, 0.440, 0.93, 0.6);
        rule(page, 0.62, 0.547, 0.93, 1.0);

        label(page, 0.70, 0.070, 16, true, "FATURA");
        label(page, 0.07, 0.119, 10, false, "NIF");
        label(page, 0.07, 0.145, 9, false, "Rua da Demonstração, 12 · 1000-001 Lisboa");
        label(page, 0.07, 0.163, 9, false, "geral@" + emailHost(fields.supplierName()) + " · 210 000 000");
        label(page, 0.62, 0.119, 10, false, "N.º");
        label(page, 0.62, 0.151, 10, false, "Data");

        label(page, 0.07, 0.220, 10, true, "Cliente");
        label(page, 0.07, 0.242, 10, false, "DocGrid Demonstração, Lda.");
        label(page, 0.07, 0.260, 10, false, "NIF 501442889 · Av. da Liberdade, 100 · 1250-144 Lisboa");

        label(page, 0.07, 0.363, 10, true, "Descrição");
        label(page, 0.62, 0.363, 10, true, "Qtd.");
        label(page, 0.86, 0.363, 10, true, "Valor");
        label(page, 0.07, 0.410, 10, false, invoice.suggestedCategory());
        label(page, 0.63, 0.410, 10, false, "1");
        amountLabel(page, 0.93, 0.410, 10, fields.netAmount());

        label(page, 0.52, 0.499, 10, false, "Base tributável");
        label(page, 0.07, 0.531, 10, false, "IVA à taxa de");
        label(page, 0.51, 0.531, 10, false, "%");
        label(page, 0.52, 0.582, 12, true, "TOTAL A PAGAR");

        label(page, 0.07, 0.940, 8, false, "Documento de demonstração gerado pelo DocGrid. Não tem valor fiscal.");

        value(page, geometries, ExtractedFieldName.SUPPLIER_NAME, fields.supplierName(), true, false);
        value(page, geometries, ExtractedFieldName.SUPPLIER_TAX_ID, fields.supplierTaxId(), false, false);
        value(page, geometries, ExtractedFieldName.INVOICE_NUMBER, fields.invoiceNumber(), false, false);
        value(page, geometries, ExtractedFieldName.ISSUE_DATE, date(fields.issueDate()), false, false);
        value(page, geometries, ExtractedFieldName.NET_AMOUNT, amount(fields.netAmount()), false, true);
        value(page, geometries, ExtractedFieldName.VAT_RATE, rate(fields.vatRate()), false, true);
        value(page, geometries, ExtractedFieldName.VAT_AMOUNT, amount(fields.vatAmount()), false, true);
        value(page, geometries, ExtractedFieldName.TOTAL_AMOUNT, amount(fields.totalAmount()), true, true);
        return page.toString();
    }

    /**
     * Um valor extraído, desenhado dentro da sua caixa: o tamanho da letra vem da altura da
     * caixa e o texto encosta a uma das margens — à direita nos montantes, como em qualquer
     * fatura. Um campo que a extração não leu não tem caixa e não se desenha: a fatura
     * chega mesmo sem ele.
     */
    private static void value(
            StringBuilder page,
            Map<ExtractedFieldName, FieldGeometry> geometries,
            ExtractedFieldName field,
            String text,
            boolean bold,
            boolean alignRight) {
        FieldGeometry geometry = geometries.get(field);
        if (geometry == null || text == null) {
            return;
        }
        double left = minX(geometry) * PAGE_WIDTH;
        double right = maxX(geometry) * PAGE_WIDTH;
        double top = minY(geometry) * PAGE_HEIGHT;
        double bottom = maxY(geometry) * PAGE_HEIGHT;

        double fontSize = Math.min(16, (bottom - top) * 0.62);
        // A linha de base fica acima do fundo da caixa o suficiente para as descidas
        // (o "ç", o "g") caberem dentro dela.
        double baseline = PAGE_HEIGHT - bottom + fontSize * 0.22;
        double x = alignRight ? right - estimatedWidth(text, fontSize) - 2 : left + 2;
        text(page, x, baseline, fontSize, bold, text);
    }

    /**
     * O valor da linha de artigo, encostado à direita da coluna. Não é um campo extraído —
     * não tem caixa nem confiança — mas sem ele a coluna "Valor" ficava vazia e a fatura
     * deixava de parecer uma fatura.
     */
    private static void amountLabel(
            StringBuilder page, double rightNorm, double yNorm, double fontSize, BigDecimal value) {
        String text = amount(value);
        if (text == null) {
            return;
        }
        double x = rightNorm * PAGE_WIDTH - estimatedWidth(text, fontSize);
        text(page, x, (1 - yNorm) * PAGE_HEIGHT, fontSize, false, text);
    }

    /** Texto fixo da fatura, posicionado em coordenadas normalizadas como os valores. */
    private static void label(
            StringBuilder page, double xNorm, double yNorm, double fontSize, boolean bold, String text) {
        text(page, xNorm * PAGE_WIDTH, (1 - yNorm) * PAGE_HEIGHT, fontSize, bold, text);
    }

    private static void text(StringBuilder page, double x, double y, double fontSize, boolean bold, String text) {
        page.append("BT /%s %s Tf 1 0 0 1 %s %s Tm (%s) Tj ET\n"
                .formatted(bold ? "F2" : "F1", number(fontSize), number(x), number(y), escape(text)));
    }

    /** Um filete horizontal: um retângulo muito baixo é mais barato do que um traço. */
    private static void rule(StringBuilder page, double fromX, double yNorm, double toX, double thickness) {
        page.append("0.75 0.75 0.75 rg\n");
        page.append("%s %s %s %s re f\n"
                .formatted(
                        number(fromX * PAGE_WIDTH),
                        number((1 - yNorm) * PAGE_HEIGHT),
                        number((toX - fromX) * PAGE_WIDTH),
                        number(thickness)));
        page.append("0 0 0 rg\n");
    }

    /**
     * A largura aproximada de um texto em Helvetica, só para alinhar à direita. As
     * larguras reais estão na métrica da fonte; 0,52 em de média erra por dois ou três
     * pontos num montante, e isso não se vê.
     */
    private static double estimatedWidth(String text, double fontSize) {
        return text.length() * fontSize * 0.52;
    }

    /** O documento inteiro à volta do conteúdo: objetos, tabela de referências e trailer. */
    private static byte[] document(String slug, byte[] content) {
        ByteArrayOutputStream pdf = new ByteArrayOutputStream();
        List<Integer> offsets = new ArrayList<>();

        append(pdf, "%PDF-1.4\n");
        append(pdf, MARKER_PREFIX + slug + "\n");

        offsets.add(pdf.size());
        append(pdf, "1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n");

        offsets.add(pdf.size());
        append(pdf, "2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n");

        offsets.add(pdf.size());
        append(pdf, """
                3 0 obj
                << /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] \
                /Resources << /Font << /F1 5 0 R /F2 6 0 R >> >> /Contents 4 0 R >>
                endobj
                """);

        offsets.add(pdf.size());
        append(pdf, "4 0 obj\n<< /Length %d >>\nstream\n".formatted(content.length));
        pdf.writeBytes(content);
        append(pdf, "endstream\nendobj\n");

        offsets.add(pdf.size());
        append(pdf, font(5, "Helvetica"));

        offsets.add(pdf.size());
        append(pdf, font(6, "Helvetica-Bold"));

        int startXref = pdf.size();
        append(pdf, "xref\n0 %d\n".formatted(offsets.size() + 1));
        append(pdf, "0000000000 65535 f \n");
        offsets.forEach(offset -> append(pdf, "%010d 00000 n \n".formatted(offset)));
        append(
                pdf,
                "trailer\n<< /Size %d /Root 1 0 R >>\nstartxref\n%d\n%%%%EOF\n"
                        .formatted(offsets.size() + 1, startXref));
        return pdf.toByteArray();
    }

    private static String font(int objectNumber, String baseFont) {
        return "%d 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /%s /Encoding /WinAnsiEncoding >>\nendobj\n"
                .formatted(objectNumber, baseFont);
    }

    private static void append(ByteArrayOutputStream pdf, String ascii) {
        pdf.writeBytes(ascii.getBytes(WIN_ANSI));
    }

    /** Os parênteses e a barra delimitam uma string no PDF: escapam-se sempre. */
    private static String escape(String text) {
        return text.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)");
    }

    private static String number(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String date(LocalDate date) {
        return date == null ? null : date.format(PORTUGUESE_DATE);
    }

    private static String rate(BigDecimal rate) {
        return rate == null ? null : rate.stripTrailingZeros().toPlainString();
    }

    /** Montantes como uma fatura portuguesa os escreve: 1.234,56 €. */
    private static String amount(BigDecimal value) {
        if (value == null) {
            return null;
        }
        String plain = value.setScale(2, RoundingMode.HALF_UP).toPlainString();
        String integerPart = plain.substring(0, plain.indexOf('.'));
        String decimals = plain.substring(plain.indexOf('.') + 1);
        StringBuilder grouped = new StringBuilder();
        for (int i = 0; i < integerPart.length(); i++) {
            if (i > 0 && (integerPart.length() - i) % 3 == 0) {
                grouped.append('.');
            }
            grouped.append(integerPart.charAt(i));
        }
        return String.format(Locale.ROOT, "%s,%s €", grouped, decimals);
    }

    /** Um domínio plausível a partir do nome do fornecedor, só para o cabeçalho ter cara. */
    private static String emailHost(String supplierName) {
        String firstWord = supplierName.split("[ ,]")[0].toLowerCase(Locale.ROOT);
        return Normalizer.normalize(firstWord, Normalizer.Form.NFD).replaceAll("[^a-z]", "") + ".pt";
    }

    private static double minX(FieldGeometry geometry) {
        return geometry.polygon().stream()
                .mapToDouble(FieldGeometry.Point::x)
                .min()
                .orElseThrow();
    }

    private static double maxX(FieldGeometry geometry) {
        return geometry.polygon().stream()
                .mapToDouble(FieldGeometry.Point::x)
                .max()
                .orElseThrow();
    }

    private static double minY(FieldGeometry geometry) {
        return geometry.polygon().stream()
                .mapToDouble(FieldGeometry.Point::y)
                .min()
                .orElseThrow();
    }

    private static double maxY(FieldGeometry geometry) {
        return geometry.polygon().stream()
                .mapToDouble(FieldGeometry.Point::y)
                .max()
                .orElseThrow();
    }
}
