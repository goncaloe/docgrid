package com.docgrid.document;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import software.amazon.awssdk.services.s3.S3Client;

import com.docgrid.auth.DemoIdentityConfiguration;
import com.docgrid.document.dto.FileUrlResponse;
import com.docgrid.document.dto.UploadUrlRequest;
import com.docgrid.document.dto.UploadUrlResponse;
import com.docgrid.support.LocalStackContainerConfiguration;
import com.docgrid.support.PostgresContainerConfiguration;

/**
 * O equivalente ao {@code curl} + {@code curl --upload-file} do critério de aceitação,
 * ponta a ponta: contexto Spring, Postgres real e LocalStack real. Pede a autorização,
 * envia o ficheiro direto para o S3, confirma o objeto no bucket, e volta a descarregá-lo
 * pelo URL de leitura.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import({PostgresContainerConfiguration.class, LocalStackContainerConfiguration.class, DemoIdentityConfiguration.class})
class DocumentUploadFlowTest {

    private static final byte[] FILE = "conteúdo da fatura de setembro".getBytes(UTF_8);

    private final HttpClient http = HttpClient.newHttpClient();

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private S3Client s3;

    @Test
    void authorizesUploadsFileToS3AndReadsItBack() throws Exception {
        UploadUrlRequest request = new UploadUrlRequest("fatura setembro.pdf", "application/pdf", (long) FILE.length);

        ResponseEntity<UploadUrlResponse> authorization =
                rest.postForEntity("/api/documents/upload-url", request, UploadUrlResponse.class);

        assertThat(authorization.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UploadUrlResponse url = authorization.getBody();
        assertThat(url).isNotNull();
        assertThat(url.storageKey()).matches("org/[0-9a-fA-F-]{36}/\\d{4}/\\d{2}/[0-9a-fA-F-]{36}\\.pdf");
        assertThat(url.httpMethod()).isEqualTo("PUT");

        HttpResponse<Void> put = http.send(
                HttpRequest.newBuilder(url.uploadUrl())
                        .header("Content-Type", "application/pdf")
                        .PUT(BodyPublishers.ofByteArray(FILE))
                        .build(),
                BodyHandlers.discarding());
        assertThat(put.statusCode()).isEqualTo(200);

        assertThat(s3.headObject(head -> head.bucket(LocalStackContainerConfiguration.BUCKET)
                                .key(url.storageKey()))
                        .contentLength())
                .isEqualTo((long) FILE.length);

        FileUrlResponse fileUrl =
                rest.getForObject("/api/documents/{id}/file-url", FileUrlResponse.class, url.documentId());
        HttpResponse<byte[]> download =
                http.send(HttpRequest.newBuilder(fileUrl.url()).GET().build(), BodyHandlers.ofByteArray());

        assertThat(download.statusCode()).isEqualTo(200);
        assertThat(download.body()).isEqualTo(FILE);
    }

    @Test
    void rejectsADisallowedFileTypeWith400ProblemJson() {
        UploadUrlRequest request = new UploadUrlRequest("gato.exe", "application/x-msdownload", 512L);

        ResponseEntity<String> response = rest.postForEntity("/api/documents/upload-url", request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(response.getBody()).contains("não aceite");
    }

    @Test
    void rejectsAMalformedRequestWith400ProblemJson() {
        UploadUrlRequest request = new UploadUrlRequest(" ", "application/pdf", 512L);

        ResponseEntity<String> response = rest.postForEntity("/api/documents/upload-url", request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void fileUrlForAnUnknownDocumentIs404() {
        ResponseEntity<String> response =
                rest.getForEntity("/api/documents/{id}/file-url", String.class, UUID.randomUUID());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
