package com.aromatica.common.search;

import java.util.List;

/** Единый формат страницы для всех /search. */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last,
        boolean empty
) {}
