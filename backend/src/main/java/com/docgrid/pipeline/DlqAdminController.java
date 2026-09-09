package com.docgrid.pipeline;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.docgrid.pipeline.dto.DlqMessageView;
import com.docgrid.pipeline.dto.RedriveSummary;

/**
 * Administração da dead-letter queue: ver o que falhou e mandá-lo reprocessar.
 *
 * <p>Sem autenticação nem papéis — isso é a etapa 06, que fecha estes endpoints atrás de
 * um papel de administração. Até lá, {@code /api/admin/**} é território de confiança.
 */
@RestController
@RequestMapping("/api/admin/dlq")
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
