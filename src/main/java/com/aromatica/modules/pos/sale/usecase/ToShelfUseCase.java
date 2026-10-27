package com.aromatica.modules.pos.sale.usecase;

import com.aromatica.modules.inventory.doc.model.DocType;
import com.aromatica.modules.inventory.doc.model.StockDoc;
import com.aromatica.modules.inventory.warehouse.service.WarehouseLookup;
import com.aromatica.modules.masterdata.catalog.product.model.CatalogProduct;
import com.aromatica.modules.masterdata.catalog.product.service.ProductService;
import com.aromatica.modules.masterdata.catalog.product.service.ReadyProductService;
import com.aromatica.modules.pos.sale.model.ReceiptLine;
import com.aromatica.modules.pos.sale.model.ToShelf;
import com.aromatica.modules.pos.sale.service.BaseConsumption;
import com.aromatica.modules.pos.sale.service.ReceiptLines;
import com.aromatica.modules.pos.sale.service.ReceiptProducts;
import com.aromatica.modules.pos.sale.service.ShiftDocPoster;
import com.aromatica.modules.pos.shift.model.PosShift;
import com.aromatica.modules.pos.shift.service.PosShiftService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ToShelfUseCase {

    PosShiftService shiftService;
    ProductService productService;
    /** Общая последовательность документов смены — см. ShiftDocPoster. */
    ShiftDocPoster shiftDocs;
    /** Полка склада смены: туда ложится готовая вещь. */
    WarehouseLookup warehouseLookup;
    /** Готовое: во что превращается налитое, когда ложится на полку. */
    ReadyProductService readyProductService;
    BaseConsumption baseConsumption;
    Clock clock;

    /**
     * «На полку» до оплаты: налили флакон, а клиент передумал. Документ — сборка в смене:
     * масло, вода и атомайзер уходят со склада, на полку ложится готовая вещь.
     */
    @Transactional
    public StockDoc handle(
            UUID shiftId,
            ToShelf command,
            String login
    ) {
        PosShift shift = shiftService.lockOpenShift(shiftId, login);
        UUID shelf = warehouseLookup.shelfOf(shift.getWarehouseId());
        List<ReceiptLine> parts = baseConsumption.withBaseConsumption(command.lines(),
                ReceiptProducts.load(productService, command.lines()));
        CatalogProduct ready = readyProductService.findOrCreate(parts.stream()
                .map(l -> new ReadyProductService.Part(l.productId(), l.qty())).toList());
        List<ReceiptLine> lines = new ArrayList<>();
        parts.forEach(l -> lines.add(ReceiptLine.free(l.productId(), l.qty(), null, null)));
        lines.add(new ReceiptLine(ready.getId(), BigDecimal.ONE,
                ready.getPriceMinor() != null ? ready.getPriceMinor() : 0, 0, false,
                null, null, null, null, null));
        StockDoc doc = StockDoc.draft(DocType.ASSEMBLY, LocalDateTime.now(clock), shift.getWarehouseId(),
                command.comment() != null ? command.comment() : "На полку: " + ready.getName(),
                ReceiptLines.toDocLines(lines));
        doc.setWarehouseToId(shelf);
        return shiftDocs.post(doc, shift, login);
    }
}
