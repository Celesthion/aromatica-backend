package com.aromatica.common.search;

import java.util.List;

/**
 * Страница без общего числа — для журналов, растущих годами: count(*) на каждую страницу
 * дороже самой страницы. Продолжение узнаём, запросив на одну строку больше.
 */
public record SliceResponse<T>(
        List<T> content,
        int page,
        boolean hasNext
) {}
