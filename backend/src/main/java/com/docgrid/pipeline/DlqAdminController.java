package com.docgrid.pipeline;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.docgrid.pipeline.dto.DlqMessageView;
import com.docgrid.pipeline.dto.RedriveSummary;

/**
 * Administração da dead-letter queue: ver o que falhou e mandá-lo reprocessar. Só
 * {@code ADMIN} — não há organização a filtrar aqui, a fila é de todo o sistema.
 */
@RestController
@RequestMapping("/api/admin/dlq")
@PreAuthorize("hasRole('ADMIN')")
class DlqAdminController {

    private final DlqAdmin dlq;

    DlqAdminController(DlqAdmin dlq) {
        this.dlq = dlq;
    }

    /** As mensagens paradas na DLQ. Espreitar não as consome. */
    @GetMapping
    List<DlqMessageView> list() {
        return dlq.list();
    }

    /** Devolve todas as mensagens da DLQ à fila principal para uma nova tentativa. */
    @PostMapping("/redrive")
    RedriveSummary redrive() {
        return dlq.redriveAll();
    }
}
