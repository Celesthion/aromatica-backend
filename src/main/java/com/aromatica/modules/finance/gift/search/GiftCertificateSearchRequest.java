package com.aromatica.modules.finance.gift.search;

import com.aromatica.common.search.BaseSearchRequest;
import com.aromatica.modules.finance.gift.model.GiftCertificate;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Sort;

@Data
@EqualsAndHashCode(callSuper = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class GiftCertificateSearchRequest extends BaseSearchRequest<GiftCertificateSort> {

    /** Подстрока по коду бланка либо по имени/телефону покупателя. */
    String q;
    GiftCertificate.Status status;

    @Override
    protected Sort.Direction getDefaultDirection() {
        return Sort.Direction.DESC;
    }

    @Override
    protected GiftCertificateSort getDefaultSortBy() {
        return GiftCertificateSort.CREATED;
    }
}
