package com.docgrid.extraction;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.textract.TextractClient;
import software.amazon.awssdk.services.textract.model.AnalyzeExpenseRequest;
import software.amazon.awssdk.services.textract.model.Document;

/**
 * A extração de verdade: o {@code AnalyzeExpense} do Textract.
 *
 * <p>Só existe no perfil {@code aws} — em local e nos testes o cliente nem sequer é
 * construído (o LocalStack não tem Textract, e é para isso que o stub existe). Trocar de
 * perfil troca de implementação, sem tocar em código de negócio: quem consome a
 * {@link DocumentExtractor} não sabe qual das duas está a correr.
 *
 * <p>O que chega ao resto do sistema é o {@link ExtractionResult} — o vocabulário do
 * Textract morre no {@link TextractNormalizer}, a dois passos daqui.
 */
@Component
@Profile("aws")
public class TextractExtractor implements DocumentExtractor {

    private final TextractClient textract;
    private final ExtractionProperties properties;

    public TextractExtractor(TextractClient textract, ExtractionProperties properties) {
        this.textract = textract;
        this.properties = properties;
    }

    @Override
    public ExtractionResult extract(byte[] content, String contentType) {
        try {
            var response = textract.analyzeExpense(AnalyzeExpenseRequest.builder()
                    // Document é um tipo do pacote textract; o com.docgrid.document.Document é
                    // outra coisa, e a colisão resolve-se aqui, num só sítio.
                    .document(Document.builder()
                            .bytes(SdkBytes.fromByteArray(content))
                            .build())
                    .build());
            return TextractNormalizer.fromResponse(response);
        } catch (RuntimeException e) {
            throw TextractError.translate(e);
        }
    }

    @Override
    public ExtractorStatus status() {
        // Honesto em vez de verde: as análises pagam-se e o Textract não tem operação
        // de ping, portanto não se contacta o serviço. O indicador diz que o motor está
        // configurado, e em que região — nada mais.
        return new ExtractorStatus(
                "textract",
                true,
                "região " + properties.textract().region() + "; o serviço não foi contactado pelo health check");
    }
}
