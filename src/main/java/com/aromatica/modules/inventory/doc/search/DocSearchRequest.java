package com.aromatica.modules.inventory.doc.search;

import com.aromatica.common.search.BaseSearchRequest;
import com.aromatica.modules.inventory.doc.model.DocStatus;
import com.aromatica.modules.inventory.doc.model.DocType;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Sort;

import java.time.LocalDateTime;

/**
 * Поиск документов: тип, статус, период, q (целое — номер документа, текст — имя
 * контрагента). По умолчанию свежие сверху.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class DocSearchRequest extends BaseSearchRequest<DocSort> {

    DocType type;
    DocStatus status;
    LocalDateTime from;
    LocalDateTime to;
    String q;

    public DocSearchRequest() {
        setDirection(Sort.Direction.DESC);
    }

    @Override
    protected DocSort getDefaultSortBy() {
        return DocSort.DATE;
    }
}
