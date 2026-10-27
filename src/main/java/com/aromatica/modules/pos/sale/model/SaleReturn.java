package com.aromatica.modules.pos.sale.model;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/** Возврат по чеку своей смены; деньги — наличными и переводом, на сумму строк. */
public record SaleReturn(

        UUID sourceReceiptId,

        List<ReceiptLine> lines,

        long cashMinor,

        long noCashMinor,

        @Nullable
        String comment
) {
}
