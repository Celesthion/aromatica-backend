package com.aromatica.common.search;

/** Потолок размера страницы: без него клиентский size=10⁸ вытянул бы всю таблицу в память. */
public final class Paging {

    public static final int MAX_PAGE_SIZE = 200;
    public static final int DEFAULT_PAGE_SIZE = 20;

    private Paging() {
    }

    /** Отрицательный номер считаем первой страницей. */
    public static int page(Integer page) {
        return page == null || page < 0 ? 0 : page;
    }

    /** Пусто или ноль — размер по умолчанию, больше потолка — потолок. */
    public static int size(Integer size) {
        if (size == null || size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }
}
