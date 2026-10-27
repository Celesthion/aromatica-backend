package com.aromatica.modules.pos.sale.usecase;

import com.aromatica.common.error.BusinessRuleException;
import com.aromatica.modules.inventory.doc.model.DocType;
import com.aromatica.modules.inventory.doc.model.StockDoc;
import com.aromatica.modules.inventory.doc.model.StockDocLine;
import com.aromatica.modules.inventory.doc.model.WriteOffReason;
import com.aromatica.modules.inventory.stock.service.StockBalanceService;
import com.aromatica.modules.masterdata.catalog.product.model.CatalogProduct;
import com.aromatica.modules.masterdata.catalog.product.service.ProductService;
import com.aromatica.modules.pos.sale.model.SplitWriteOff;
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
import java.util.List;
import java.util.UUID;

/**
 * Списание хвоста разлива (из флакона вышло меньше, чем по учёту) документом WRITE_OFF смены.
 * Только ML; потолка нет — операция не запрещена, но видна в сводке и ленте.
 */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SplitWriteOffUseCase {

    PosShiftService shiftService;
    ProductService productService;
    StockBalanceService stockBalanceService;
    ShiftDocPoster shiftDocs;
    Clock clock;

    @Transactional
    public StockDoc handle(
            UUID shiftId,
            SplitWriteOff command,
            String login
    ) {
        PosShift shift = shiftService.lockOpenShift(shiftId, login);
        CatalogProduct product = productService.find(command.productId());
        if (product.getUnit() != CatalogProduct.Unit.ML) {
            throw new BusinessRuleException(
                    "С кассы списывается только разлив — позиция в миллилитрах");
        }
        // в минус может увести только продажа; списание сверх остатка — ошибка ввода
        BigDecimal onHand = stockBalanceService
                .qtyMap(shift.getWarehouseId(), List.of(product.getId()))
                .getOrDefault(product.getId(), BigDecimal.ZERO);
        if (onHand.compareTo(command.qty()) < 0) {
            throw new BusinessRuleException(
                    "Списать больше остатка нельзя: на складе " + plainMl(onHand) + " мл");
        }
        StockDoc doc = StockDoc.draft(DocType.WRITE_OFF, LocalDateTime.now(clock),
                shift.getWarehouseId(), writeOffComment(product, command.pouredMl()),
                List.of(StockDocLine.of(product.getId(), command.qty(), 0, 0)));
        doc.setWriteOffReason(WriteOffReason.BOTTLE_EMPTY);
        return shiftDocs.post(doc, shift, login);
    }

    private static String writeOffComment(
            CatalogProduct product,
            BigDecimal pouredMl
    ) {
        String base = "Не хватило при наливе: " + product.getName();
        return pouredMl != null ? base + ", налито " + plainMl(pouredMl) + " мл" : base;
    }

    /** 20.00 → 20, 0.50 → 0.5. */
    private static String plainMl(BigDecimal ml) {
        return ml.signum() == 0 ? "0" : ml.stripTrailingZeros().toPlainString();
    }
}
