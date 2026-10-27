package com.aromatica.modules.inventory.doc.model;

import com.aromatica.common.error.ConflictException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Переходы статуса документа: черновик ⇄ проведён, черновик → отменён. */
class StockDocStatusTest {

    private static final LocalDateTime AT = LocalDateTime.of(2026, 10, 25, 12, 0);

    @Test
    void markPosted_draftCanBeUnpostedBack() {
        StockDoc doc = new StockDoc();

        doc.markPosted(AT);
        assertThat(doc.isPosted()).isTrue();
        assertThat(doc.getPostedAt()).isEqualTo(AT);

        doc.markUnposted();
        assertThat(doc.getStatus()).isEqualTo(DocStatus.DRAFT);
        assertThat(doc.getPostedAt()).isNull();
    }

    @Test
    void markPosted_rejectsSecondPosting() {
        StockDoc doc = new StockDoc();
        doc.markPosted(AT);

        assertThatThrownBy(() -> doc.markPosted(AT))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Провести можно только черновик, статус: POSTED");
    }

    @Test
    void markUnposted_rejectsDraft() {
        assertThatThrownBy(() -> new StockDoc().markUnposted())
                .isInstanceOf(ConflictException.class)
                .hasMessage("Распровести можно только проведённый документ, статус: DRAFT");
    }

    @Test
    void cancel_allowsOnlyDraft() {
        StockDoc posted = new StockDoc();
        posted.markPosted(AT);
        assertThatThrownBy(posted::cancel).isInstanceOf(ConflictException.class);

        StockDoc draft = new StockDoc();
        draft.cancel();
        assertThat(draft.getStatus()).isEqualTo(DocStatus.CANCELLED);
        assertThatThrownBy(() -> draft.requireDraft("Редактировать"))
                .hasMessage("Редактировать можно только черновик, статус: CANCELLED");
    }
}
