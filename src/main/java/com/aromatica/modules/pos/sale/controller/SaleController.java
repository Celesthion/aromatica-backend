package com.aromatica.modules.pos.sale.controller;

import com.aromatica.modules.finance.gift.dtos.GiftDtos.CertificateCheck;
import com.aromatica.modules.finance.gift.dtos.GiftDtos.GiftCertificateDto;
import com.aromatica.modules.finance.gift.dtos.GiftDtos.SellRequest;
import com.aromatica.modules.finance.gift.endpoint.GiftCertificateEndpoint;
import com.aromatica.modules.inventory.convert.dtos.ConvertDtos.SplitInfo;
import com.aromatica.modules.inventory.doc.dtos.DocDtos.DocDto;
import com.aromatica.modules.pos.sale.dtos.SaleDtos.PosConvertRequest;
import com.aromatica.modules.pos.sale.dtos.SaleDtos.PosShelfRequest;
import com.aromatica.modules.pos.sale.dtos.SaleDtos.PosSplitWriteOffRequest;
import com.aromatica.modules.pos.sale.dtos.SaleDtos.ReceiptRequest;
import com.aromatica.modules.pos.sale.dtos.SaleDtos.ReturnRequest;
import com.aromatica.modules.pos.sale.endpoint.SaleEndpoint;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "Касса: продажа", description = "Чеки, возвраты, сторно, «на полку», вскрытие и списание хвоста, подарочные сертификаты у прилавка.")
@RestController
@PreAuthorize("hasAnyRole('ADMIN', 'SELLER', 'SENIOR_SELLER')")
@RequestMapping("/api/pos")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SaleController {

    SaleEndpoint saleEndpoint;
    GiftCertificateEndpoint giftEndpoint;

    @Operation(summary = "Чеки смены")
    @GetMapping("/shifts/{id}/receipts")
    public List<DocDto> shiftReceipts(@PathVariable UUID id) {
        return saleEndpoint.shiftReceipts(id);
    }

    @Operation(summary = "Пробить чек")
    @PostMapping("/shifts/{id}/receipts")
    public DocDto createReceipt(
            @PathVariable UUID id,
            @Valid @RequestBody ReceiptRequest request
    ) {
        return saleEndpoint.createReceipt(id, request);
    }

    @Operation(summary = "Проверить подарочный сертификат", description = "Остаток сертификата и можно ли им платить. Частота проверок ограничена.")
    @GetMapping("/gift-certificates/check")
    public CertificateCheck checkCertificate(@RequestParam String code) {
        return giftEndpoint.check(code);
    }

    @Operation(summary = "Продать подарочный сертификат", description = "Не чек: сертификат — аванс, а не выручка.")
    @PostMapping("/shifts/{id}/gift-certificates/sell")
    public GiftCertificateDto sellCertificate(
            @PathVariable UUID id,
            @Valid @RequestBody SellRequest request
    ) {
        return saleEndpoint.sellCertificate(id, request);
    }

    @Operation(summary = "Распив аромата", description = "Есть ли у аромата распив; касса спрашивает перед вскрытием.")
    @GetMapping("/convert/split")
    public SplitInfo splitInfo(@RequestParam UUID sourceProductId) {
        return saleEndpoint.splitInfo(sourceProductId);
    }

    @Operation(summary = "Вскрыть флакон в распив", description = "Флакон или тестер, у прилавка. Доступно и продавцу.")
    @PostMapping("/shifts/{id}/convert")
    public DocDto convert(
            @PathVariable UUID id,
            @Valid @RequestBody PosConvertRequest request
    ) {
        return saleEndpoint.convert(id, request);
    }

    @Operation(summary = "Списать недолив распива", description = "«Не хватило» при наливе: флакон кончился раньше учётного остатка, хвост списывается. Доступно любому кассиру без лимита; операция видна в сводке смены и в ленте.")
    @PostMapping("/shifts/{id}/split-writeoff")
    public DocDto writeOffSplit(
            @PathVariable UUID id,
            @Valid @RequestBody PosSplitWriteOffRequest request
    ) {
        return saleEndpoint.writeOffSplit(id, request);
    }

    @Operation(summary = "Оформить возврат", description = "Только по чеку. Попадает в смену, X-отчёт и расчётные наличные.")
    @PostMapping("/shifts/{id}/returns")
    public DocDto createReturn(
            @PathVariable UUID id,
            @Valid @RequestBody ReturnRequest request
    ) {
        return saleEndpoint.createReturn(id, request);
    }

    @Operation(summary = "Аннулировать чек")
    @PostMapping("/shifts/{id}/receipts/{docId}/void")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void voidReceipt(
            @PathVariable UUID id,
            @PathVariable UUID docId
    ) {
        saleEndpoint.voidReceipt(id, docId);
    }

    @Operation(summary = "Поставить на полку", description = "Налитое до оплаты уходит на полку готовой вещью.")
    @PostMapping("/shifts/{id}/shelf")
    public DocDto toShelf(
            @PathVariable UUID id,
            @Valid @RequestBody PosShelfRequest request
    ) {
        return saleEndpoint.toShelf(id, request);
    }
}
