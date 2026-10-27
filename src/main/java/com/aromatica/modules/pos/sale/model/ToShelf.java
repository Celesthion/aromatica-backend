package com.aromatica.modules.pos.sale.model;

import org.jspecify.annotations.Nullable;

import java.util.List;

/** «На полку» из чека: налитое не продали, части становятся готовой вещью на полке. */
public record ToShelf(

        List<ReceiptLine> lines,

        @Nullable
        String comment
) {
}
