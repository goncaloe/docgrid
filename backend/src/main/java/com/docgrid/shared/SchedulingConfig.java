package com.docgrid.shared;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Liga o agendamento do Spring. Serve o refresh periódico das métricas
 * ({@code pipeline.PipelineMetrics}); num processo que corre a API e o worker há um só
 * scheduler, e o refresh corre uma vez.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class SchedulingConfig {}
