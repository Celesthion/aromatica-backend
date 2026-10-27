package com.aromatica.common.search;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SearchTokensTest {

    @Test
    void of_splitsByWhitespace() {
        assertThat(SearchTokens.of("creed aventus")).containsExactly("creed", "aventus");
    }

    @Test
    void of_collapsesExtraSpacesAndTrims() {
        assertThat(SearchTokens.of("  creed   aventus  ")).containsExactly("creed", "aventus");
    }

    @Test
    void of_emptyQueryGivesNoTokens() {
        assertThat(SearchTokens.of(null)).isEmpty();
        assertThat(SearchTokens.of("")).isEmpty();
        assertThat(SearchTokens.of("   ")).isEmpty();
    }

    @Test
    void of_keepsSingleWord() {
        assertThat(SearchTokens.of("aventus")).containsExactly("aventus");
    }

    @Test
    void of_limitsTokenCount() {
        assertThat(SearchTokens.of("a b c d e f g h")).hasSize(6);
    }
}
