package com.villagebooker.dto.common;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

// stable JSON shape for paged lists (Spring's Page is not meant to be serialized directly)
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {

    public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
