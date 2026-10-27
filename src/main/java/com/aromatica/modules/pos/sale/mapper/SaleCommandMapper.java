package com.aromatica.modules.pos.sale.mapper;

import com.aromatica.modules.inventory.convert.model.ConvertOpening;
import com.aromatica.modules.inventory.doc.dtos.DocDtos.LineRequest;
import com.aromatica.modules.pos.sale.dtos.SaleDtos.PosConvertRequest;
import com.aromatica.modules.pos.sale.dtos.SaleDtos.PosShelfRequest;
import com.aromatica.modules.pos.sale.dtos.SaleDtos.PosSplitWriteOffRequest;
import com.aromatica.modules.pos.sale.dtos.SaleDtos.ReceiptRequest;
import com.aromatica.modules.pos.sale.dtos.SaleDtos.ReturnRequest;
import com.aromatica.modules.pos.sale.model.ReceiptLine;
import com.aromatica.modules.pos.sale.model.Sale;
import com.aromatica.modules.pos.sale.model.SaleReturn;
import com.aromatica.modules.pos.sale.model.SplitWriteOff;
import com.aromatica.modules.pos.sale.model.ToShelf;
import org.mapstruct.Mapper;

import java.util.List;
import java.util.UUID;

/** Запросы операций кассы → доменные команды. Отсутствующие суммы уже нули: их нормализует record запроса. */
@Mapper
public interface SaleCommandMapper {

    Sale toSale(ReceiptRequest request);

    SaleReturn toReturn(ReturnRequest request);

    ToShelf toShelf(PosShelfRequest request);

    SplitWriteOff toWriteOff(PosSplitWriteOffRequest request);

    List<ReceiptLine> toLines(List<LineRequest> lines);

    /** Объём — из карточки товара, кассир его не вводит; склад — смены. */
    default ConvertOpening toConvert(
            PosConvertRequest request,
            UUID warehouseId
    ) {
        return new ConvertOpening(request.sourceProductId(), request.sourcePcs(), null, null, null, null,
                warehouseId);
    }

    /** Скидку старые клиенты присылают процентом — здесь она становится суммой. */
    default ReceiptLine toLine(LineRequest line) {
        return new ReceiptLine(line.productId(), line.qty(), line.priceMinor(), line.resolvedDiscountMinor(),
                line.gift(), line.giftRuleId(), line.giftGroupId(), line.dilutionPct(), line.bundleId(),
                line.warehouseId());
    }
}
