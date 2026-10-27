package com.aromatica.common.search;

import java.util.Arrays;
import java.util.List;

/**
 * Поиск по словам, совпасть должно каждое (AND), порядок не важен: строкой целиком
 * не найти «creed aventus» — в имени товара между брендом и моделью тире.
 */
public final class SearchTokens {

    /** Больше слов у прилавка не набирают, а лимит бережёт БД от длинных строк. */
    private static final int MAX_TOKENS = 6;

    private SearchTokens() {}

    public static List<String> of(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        return Arrays.stream(query.trim().split("\\s+"))
                .filter(t -> !t.isBlank())
                .limit(MAX_TOKENS)
                .toList();
    }
}
