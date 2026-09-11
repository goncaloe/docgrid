package com.docgrid.document;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.docgrid.document.dto.FileUrlResponse;
import com.docgrid.document.dto.UploadUrlRequest;
import com.docgrid.document.dto.UploadUrlResponse;

/**
 * O fluxo de upload: o cliente pede uma autorização, envia o ficheiro direto para o S3 e,
 * mais tarde, pede uma autorização de leitura. O ficheiro nunca passa por aqui.
 *
 * <p>A identidade de quem submete e a organização vêm de
 * {@link com.docgrid.auth.CurrentUserProvider}, preenchido pelo token JWT do pedido.
 */
@RestController
@RequestMapping("/api/documents")
class DocumentUploadController {

    private final DocumentUploadService uploads;

    DocumentUploadController(DocumentUploadService uploads) {
        this.uploads = uploads;
    }

    @PostMapping("/upload-url")
    @ResponseStatus(HttpStatus.CREATED)
    UploadUrlResponse requestUploadUrl(@Valid @RequestBody UploadUrlRequest request) {
        return uploads.authorizeUpload(request);
    }

    @GetMapping("/{id}/file-url")
    FileUrlResponse fileUrl(@PathVariable UUID id) {
        return uploads.fileUrl(id);
    }
}
