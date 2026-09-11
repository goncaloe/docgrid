package com.docgrid.document.dto;

import java.util.List;

import org.springframework.data.domain.Page;

/**
 * Envelope de paginação por offset, estável para o cliente — não expõe a serialização
 * interna de {@link Page} do Spring Data.
 */
public record PageResponse<T>(List<T> items, int page, int size, long totalElements, int totalPages) {

    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(
                page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
