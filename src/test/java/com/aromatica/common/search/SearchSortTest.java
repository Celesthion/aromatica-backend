package com.aromatica.common.search;

import com.aromatica.modules.inventory.doc.search.DocSort;
import com.aromatica.modules.masterdata.catalog.brand.search.BrandSort;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;
import org.springframework.data.querydsl.QSort;

import static org.assertj.core.api.Assertions.assertThat;

/** Поиск листается стабильно: последним ключом сортировки всегда идёт id. */
class SearchSortTest {

    @Test
    void withIdTieBreak_appendsIdAfterSortKeys() {
        QSort sort = (QSort) SearchSort.withIdTieBreak(BrandSort.NAME.getSorting(Sort.Direction.DESC));

        assertThat(sort.getOrderSpecifiers()).hasSize(2);
        assertThat(sort.getOrderSpecifiers().get(1).getTarget().toString()).isEqualTo("brand.id");
    }

    @Test
    void withIdTieBreak_keepsMultiKeySortAndAddsId() {
        QSort sort = (QSort) SearchSort.withIdTieBreak(DocSort.DATE.getSorting(Sort.Direction.ASC));

        assertThat(sort.getOrderSpecifiers()).hasSize(3);
        assertThat(sort.getOrderSpecifiers().get(2).getTarget().toString()).isEqualTo("stockDoc.id");
    }
}
