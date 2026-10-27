package com.aromatica.modules.inventory.report.mapper;

import com.aromatica.modules.inventory.doc.model.StockDoc;
import com.aromatica.modules.inventory.doc.service.DocService;
import com.aromatica.modules.inventory.report.dtos.StockDtos.StockMovementDto;
import com.aromatica.modules.inventory.stock.model.StockMovement;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Движения → DTO: реквизиты документов страницы — одним запросом, а не по строке. */
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class StockHistoryMapper {

    DocService docService;

    public List<StockMovementDto> toDtos(List<StockMovement> movements) {
        List<UUID> docIds = movements.stream().map(StockMovement::getDocId).filter(Objects::nonNull).distinct().toList();
        Map<UUID, StockDoc> docs = new HashMap<>();
        if (!docIds.isEmpty()) {
            docService.findAllById(docIds).forEach(d -> docs.put(d.getId(), d));
        }
        return movements.stream().map(m -> toDto(m, docs.get(m.getDocId()))).toList();
    }

    /** Документ мог пропасть из базы: тогда движение показываем без его реквизитов. */
    private static StockMovementDto toDto(
            StockMovement movement,
            @Nullable StockDoc doc
    ) {
        return new StockMovementDto(movement.getId(), movement.getDocId(),
                doc != null ? doc.getDocType() : null,
                doc != null ? doc.getNumber() : 0,
                doc != null ? doc.getStatus() : null,
                movement.getQtyDelta(), movement.getCostMinor(), movement.getMoment());
    }
}
