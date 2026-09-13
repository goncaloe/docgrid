package com.docgrid.storage;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

/**
 * O {@link StorageService} sobre o S3 da AWS (ou o LocalStack, que fala o mesmo protocolo).
 *
 * <p>Só o {@link S3Presigner} emite URLs; o {@link S3Client} serve para o
 * {@link #exists(String) headObject} e o {@link #download(String) getObject}. O ficheiro
 * só passa por aqui para ser processado — o upload e a leitura pelo browser continuam a
 * ser direto, por URL assinada.
 */
@Service
class S3StorageService implements StorageService {

    private final S3Client s3;
    private final S3Presigner presigner;
    private final String bucket;

    S3StorageService(S3Client s3, S3Presigner presigner, StorageProperties props) {
        this.s3 = s3;
        this.presigner = presigner;
        this.bucket = props.bucket();
    }

    @Override
    public PresignedUrl createUploadUrl(String key, String contentType, Duration ttl) {
        PresignedPutObjectRequest presigned = presigner.presignPutObject(PutObjectPresignRequest.builder()
                .signatureDuration(ttl)
                .putObjectRequest(request -> request.bucket(bucket).key(key).contentType(contentType))
                .build());
        return new PresignedUrl(
                URI.create(presigned.url().toString()),
                "PUT",
                Map.of("Content-Type", contentType),
                presigned.expiration());
    }

    @Override
    public PresignedUrl createDownloadUrl(String key, String downloadFilename, Duration ttl) {
        GetObjectRequest get = GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .responseContentDisposition("attachment; filename=\"" + sanitize(downloadFilename) + "\"")
                .build();
        PresignedGetObjectRequest presigned = presigner.presignGetObject(GetObjectPresignRequest.builder()
                .signatureDuration(ttl)
                .getObjectRequest(get)
                .build());
        return new PresignedUrl(URI.create(presigned.url().toString()), "GET", Map.of(), presigned.expiration());
    }

    @Override
    public boolean exists(String key) {
        try {
            s3.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build());
            return true;
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return false;
            }
            throw e;
        }
    }

    @Override
    public StoredObject download(String key) {
        try {
            return new StoredObject(s3.getObjectAsBytes(
                            GetObjectRequest.builder().bucket(bucket).key(key).build())
                    .asByteArray());
        } catch (NoSuchKeyException e) {
            throw new NoSuchObjectException(key);
        }
    }

    @Override
    public void put(String key, String contentType, byte[] content) {
        s3.putObject(
                PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .contentType(contentType)
                        .build(),
                RequestBody.fromBytes(content));
    }

    /** Um nome de ficheiro não pode partir o cabeçalho {@code Content-Disposition}. */
    private static String sanitize(String filename) {
        return filename.replaceAll("[\"\\r\\n]", "_");
    }
}
