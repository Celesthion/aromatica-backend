package com.aromatica.modules.pos.sale.endpoint;

import com.aromatica.modules.finance.gift.dtos.GiftDtos.GiftCertificateDto;
import com.aromatica.modules.finance.gift.dtos.GiftDtos.SellRequest;
import com.aromatica.modules.inventory.convert.dtos.ConvertDtos.SplitInfo;
import com.aromatica.modules.inventory.doc.dtos.DocDtos.DocDto;
import com.aromatica.modules.pos.sale.dtos.SaleDtos.PosConvertRequest;
import com.aromatica.modules.pos.sale.dtos.SaleDtos.PosShelfRequest;
import com.aromatica.modules.pos.sale.dtos.SaleDtos.PosSplitWriteOffRequest;
import com.aromatica.modules.pos.sale.dtos.SaleDtos.ReceiptRequest;
import com.aromatica.modules.pos.sale.dtos.SaleDtos.ReturnRequest;

import java.util.List;
import java.util.UUID;

public interface SaleEndpoint {

    List<DocDto> shiftReceipts(UUID shiftId);

    /** Касса спрашивает до вскрытия, есть ли у аромата SKU для распива. */
    SplitInfo splitInfo(UUID sourceProductId);

    /** Вскрыть флакон или тестер в распив на складе смены. */
    DocDto convert(UUID shiftId, PosConvertRequest request);

    /** Кнопка «Не хватило» при наливе. */
    DocDto writeOffSplit(UUID shiftId, PosSplitWriteOffRequest request);

    DocDto createReceipt(UUID shiftId, ReceiptRequest request);

    DocDto createReturn(UUID shiftId, ReturnRequest request);

    DocDto toShelf(UUID shiftId, PosShelfRequest request);

    void voidReceipt(UUID shiftId, UUID docId);

    /** Деньги за сертификат ложатся в кассу смены. */
    GiftCertificateDto sellCertificate(UUID shiftId, SellRequest request);
}
