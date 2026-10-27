package com.aromatica.modules.inventory.doc.service;

import com.aromatica.modules.inventory.doc.model.DocType;
import com.aromatica.modules.inventory.doc.posting.DocPostings;
import com.aromatica.modules.inventory.doc.posting.MovePosting;
import com.aromatica.modules.inventory.doc.posting.SupplyPosting;
import com.aromatica.modules.inventory.doc.repository.StockDocRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/** Реестр стратегий проведения: новый тип документа без правил не даст приложению запуститься. */
class DocPostingsTest {

    @Test
    void of_everyDocTypeHasItsOwnPosting() {
        DocPostings postings = PostingServiceTest.allPostings(mock(StockDocRepository.class));

        for (DocType type : DocType.values()) {
            assertThat(postings.of(type).type()).isEqualTo(type);
        }
    }

    @Test
    void constructor_failsOnMissingType() {
        assertThatThrownBy(() -> new DocPostings(List.of(new SupplyPosting(), new MovePosting())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SALE");
    }

    @Test
    void constructor_failsOnDuplicateType() {
        assertThatThrownBy(() -> new DocPostings(List.of(new SupplyPosting(), new SupplyPosting())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SUPPLY");
    }
}
