package com.docgrid.storage;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.containers.localstack.LocalStackContainer.Service;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * O {@link S3StorageService} contra um S3 a sério (LocalStack). Monta o cliente exatamente
 * como a aplicação o monta ({@link StorageConfig#buildS3Client}), para não haver um teste
 * que passa aqui e falha em produção.
 */
@Testcontainers
class S3StorageServiceTest {

    private static final String BUCKET = "docgrid-storage-it";
    private static final Duration FIVE_MINUTES = Duration.ofMinutes(5);

    @Container
    static final LocalStackContainer LOCALSTACK = new LocalStackContainer(
                    DockerImageName.parse("localstack/localstack:4.9.2"))
            .withServices(Service.S3)
            // Sem isto o LocalStack não valida a assinatura nem a expiração dos URLs.
            .withEnv("S3_SKIP_SIGNATURE_VALIDATION", "0");

    private static S3Client s3;
    private static S3StorageService storage;

    @BeforeAll
    static void setUp() {
        StorageProperties props = new StorageProperties(
                BUCKET,
                LOCALSTACK.getRegion(),
                LOCALSTACK.getEndpoint().toString(),
                LOCALSTACK.getAccessKey(),
                LOCALSTACK.getSecretKey());
        s3 = StorageConfig.buildS3Client(props);
        s3.createBucket(b -> b.bucket(BUCKET));
        storage = new S3StorageService(s3, StorageConfig.buildS3Presigner(props), props);
    }

    @Test
    void issuesAnUploadUrlThatPutsTheFileInTheBucket() throws Exception {
        String key = "org/a/2026/09/upload.pdf";
        byte[] body = "conteúdo da fatura".getBytes(UTF_8);

        PresignedUrl url = storage.createUploadUrl(key, "application/pdf", FIVE_MINUTES);
        HttpResponse<Void> response = put(url, body);

        assertThat(url.httpMethod()).isEqualTo("PUT");
        assertThat(url.expiresAt()).isAfter(Instant.now());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(s3.headObject(b -> b.bucket(BUCKET).key(key)).contentLength())
                .isEqualTo(body.length);
    }

    @Test
    void issuesADownloadUrlThatReturnsTheFileAsAnAttachment() throws Exception {
        String key = "org/a/2026/09/download.pdf";
        byte[] body = "fatura para descarregar".getBytes(UTF_8);
        s3.putObject(b -> b.bucket(BUCKET).key(key).contentType("application/pdf"), RequestBody.fromBytes(body));

        PresignedUrl url = storage.createDownloadUrl(key, "fatura original.pdf", FIVE_MINUTES);
        HttpResponse<byte[]> response = HttpClient.newHttpClient()
                .send(HttpRequest.newBuilder(url.url()).GET().build(), BodyHandlers.ofByteArray());

        assertThat(url.httpMethod()).isEqualTo("GET");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo(body);
        assertThat(response.headers().firstValue("Content-Disposition"))
                .hasValue("attachment; filename=\"fatura original.pdf\"");
    }

    @Test
    void refusesAnExpiredUrl() throws Exception {
        String key = "org/a/2026/09/expirado.pdf";

        PresignedUrl url = storage.createUploadUrl(key, "application/pdf", Duration.ofSeconds(2));
        Thread.sleep(3_000);
        HttpResponse<Void> response = put(url, "tarde demais".getBytes(UTF_8));

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(storage.exists(key)).isFalse();
    }

    @Test
    void reportsWhetherAnObjectExists() {
        String key = "org/a/2026/09/existe.pdf";
        assertThat(storage.exists(key)).isFalse();

        s3.putObject(b -> b.bucket(BUCKET).key(key), RequestBody.fromString("x"));

        assertThat(storage.exists(key)).isTrue();
    }

    @Test
    void downloadsTheObjectContent() {
        String key = "org/a/2026/09/processar.pdf";
        byte[] body = "fatura para o worker processar".getBytes(UTF_8);
        s3.putObject(b -> b.bucket(BUCKET).key(key), RequestBody.fromBytes(body));

        assertThat(storage.download(key).content()).isEqualTo(body);
    }

    @Test
    void downloadOfAMissingObjectIsADistinctPermanentError() {
        assertThatThrownBy(() -> storage.download("org/a/2026/09/nunca-subiu.pdf"))
                .isInstanceOf(NoSuchObjectException.class)
                .hasMessageContaining("nunca-subiu.pdf");
    }

    @Test
    void refusesATamperedSignature() throws Exception {
        String key = "org/a/2026/09/privado.pdf";
        s3.putObject(b -> b.bucket(BUCKET).key(key), RequestBody.fromString("segredo"));

        PresignedUrl valid = storage.createDownloadUrl(key, "privado.pdf", FIVE_MINUTES);
        URI tampered = URI.create(
                valid.url().toString().replaceAll("X-Amz-Signature=[a-f0-9]+", "X-Amz-Signature=" + "0".repeat(64)));
        HttpResponse<Void> response = HttpClient.newHttpClient()
                .send(HttpRequest.newBuilder(tampered).GET().build(), BodyHandlers.discarding());

        assertThat(response.statusCode()).isEqualTo(403);
    }

    private static HttpResponse<Void> put(PresignedUrl url, byte[] body) throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(url.url()).PUT(BodyPublishers.ofByteArray(body));
        url.requiredHeaders().forEach(request::header);
        return HttpClient.newHttpClient().send(request.build(), BodyHandlers.discarding());
    }
}
