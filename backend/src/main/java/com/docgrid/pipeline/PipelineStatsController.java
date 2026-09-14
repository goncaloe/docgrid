package com.docgrid.pipeline;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.docgrid.pipeline.dto.PipelineStats;

/**
 * O estado do pipeline, para a administração — o mesmo desenho de {@link DlqAdminController}:
 * sem organização a filtrar, o pipeline é de todo o sistema. É o que o dashboard de
 * administração vai consumir (etapa 12).
 */
@RestController
@RequestMapping("/api/admin/pipeline")
@PreAuthorize("hasRole('ADMIN')")
class PipelineStatsController {

    private final PipelineStatsService stats;

    PipelineStatsController(PipelineStatsService stats) {
        this.stats = stats;
    }

    @GetMapping("/stats")
    PipelineStats stats() {
        return stats.current();
    }
}
