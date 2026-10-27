package com.aromatica.modules.pos.shift.search;

import com.aromatica.common.search.BaseSearchRequest;
import com.aromatica.modules.pos.shift.model.PosShift;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Sort;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ShiftSearchRequest extends BaseSearchRequest<ShiftSort> {

    /** Число ищется как точный номер смены. */
    String q;
    PosShift.Status status;
    LocalDateTime from;
    LocalDateTime to;

    /** По умолчанию свежие сверху: с ними и работают. */
    @Override
    protected Sort.Direction getDefaultDirection() {
        return Sort.Direction.DESC;
    }

    @Override
    protected ShiftSort getDefaultSortBy() {
        return ShiftSort.DATE;
    }
}
