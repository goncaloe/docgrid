package com.docgrid.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/**
 * O parser lê um formato estável desde 2014, mas há dois casos que um {@code readTree}
 * ingénuo deixaria passar: o {@code s3:TestEvent} do arranque e a chave url-encoded.
 * E há um terceiro que não pode passar em silêncio: uma mensagem que não é sequer um
 * evento do S3.
 */
class S3EventNotificationParserTest {

    private final S3EventNotificationParser parser = new S3EventNotificationParser(new ObjectMapper());

    @Test
    void readsBucketAndKeyFromAnObjectCreatedRecord() {
        String body = objectCreated("docgrid-documents", "org/abc/2026/09/fatura.pdf");

        List<S3ObjectReference> references = parser.parse(body);

        assertThat(references)
                .containsExactly(new S3ObjectReference("docgrid-documents", "org/abc/2026/09/fatura.pdf"));
    }

    @Test
    void urlDecodesTheObjectKey() {
        String body = objectCreated("docgrid-documents", "org/abc/2026/09/fatura+de+setembro.pdf");

        List<S3ObjectReference> references = parser.parse(body);

        assertThat(references)
                .singleElement()
                .extracting(S3ObjectReference::key)
                .isEqualTo("org/abc/2026/09/fatura de setembro.pdf");
    }

    @Test
    void readsEveryObjectCreatedRecordInTheMessage() {
        String body = """
                {"Records":[
                  {"eventName":"ObjectCreated:Put","s3":{"bucket":{"name":"b"},"object":{"key":"k1"}}},
                  {"eventName":"ObjectCreated:CompleteMultipartUpload","s3":{"bucket":{"name":"b"},"object":{"key":"k2"}}}
                ]}""";

        List<S3ObjectReference> references = parser.parse(body);

        assertThat(references).extracting(S3ObjectReference::key).containsExactly("k1", "k2");
    }

    @Test
    void ignoresTheTestEventS3SendsWhenTheNotificationIsConfigured() {
        String body =
                "{\"Event\":\"s3:TestEvent\",\"Bucket\":\"docgrid-documents\",\"Time\":\"2026-09-09T00:00:00.000Z\"}";

        assertThat(parser.parse(body)).isEmpty();
    }

    @Test
    void skipsRecordsThatAreNotObjectCreated() {
        String body =
                "{\"Records\":[{\"eventName\":\"ObjectRemoved:Delete\",\"s3\":{\"bucket\":{\"name\":\"b\"},\"object\":{\"key\":\"k\"}}}]}";

        assertThat(parser.parse(body)).isEmpty();
    }

    @Test
    void rejectsABodyThatIsNotJson() {
        assertThatThrownBy(() -> parser.parse("not json at all"))
                .isInstanceOf(MalformedS3EventException.class)
                .hasMessageContaining("ilegível");
    }

    @Test
    void rejectsAJsonBodyWithoutRecords() {
        assertThatThrownBy(() -> parser.parse("{\"hello\":\"world\"}")).isInstanceOf(MalformedS3EventException.class);
    }

    @Test
    void rejectsARecordMissingTheObjectKey() {
        String body =
                "{\"Records\":[{\"eventName\":\"ObjectCreated:Put\",\"s3\":{\"bucket\":{\"name\":\"b\"},\"object\":{}}}]}";

        assertThatThrownBy(() -> parser.parse(body)).isInstanceOf(MalformedS3EventException.class);
    }

    @Test
    void rejectsARecordMissingTheBucketName() {
        String body =
                "{\"Records\":[{\"eventName\":\"ObjectCreated:Put\",\"s3\":{\"bucket\":{},\"object\":{\"key\":\"k\"}}}]}";

        assertThatThrownBy(() -> parser.parse(body)).isInstanceOf(MalformedS3EventException.class);
    }

    private static String objectCreated(String bucket, String key) {
        return """
                {"Records":[{"eventName":"ObjectCreated:Put","s3":{"bucket":{"name":"%s"},"object":{"key":"%s"}}}]}""".formatted(bucket, key);
    }
}
