package com.aromatica.common.search;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Function;

@Component
public class PageMapper {

    public <E, R> PageResponse<R> toResponse(
            Page<E> page,
            Function<E, R> mapper
    ) {
        return toResponse(page, page.getContent().stream().map(mapper).toList());
    }

    public <T> PageResponse<T> toResponse(
            Page<?> page,
            List<T> content
    ) {
        return new PageResponse<>(content, page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(),
                page.isFirst(), page.isLast(), page.isEmpty());
    }

    public <E, R> SliceResponse<R> toSlice(
            Slice<E> slice,
            Function<E, R> mapper
    ) {
        return toSlice(slice, slice.getContent().stream().map(mapper).toList());
    }

    public <T> SliceResponse<T> toSlice(
            Slice<?> slice,
            List<T> content
    ) {
        return new SliceResponse<>(content, slice.getNumber(), slice.hasNext());
    }
}
