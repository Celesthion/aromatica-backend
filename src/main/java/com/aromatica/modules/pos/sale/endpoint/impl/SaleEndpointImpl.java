package com.aromatica.modules.pos.sale.endpoint.impl;

import com.aromatica.common.error.BusinessRuleException;
import com.aromatica.modules.finance.gift.dtos.GiftDtos.GiftCertificateDto;
import com.aromatica.modules.finance.gift.dtos.GiftDtos.SellRequest;
import com.aromatica.modules.finance.gift.mapper.GiftCertificateMapper;
import com.aromatica.modules.finance.gift.service.GiftCertificateService;
import com.aromatica.modules.inventory.convert.dtos.ConvertDtos.SplitInfo;
import com.aromatica.modules.inventory.convert.mapper.ConvertMapper;
import com.aromatica.modules.inventory.convert.service.ConvertService;
import com.aromatica.modules.inventory.doc.dtos.DocDtos.DocDto;
import com.aromatica.modules.inventory.doc.mapper.DocMapper;
import com.aromatica.modules.pos.sale.dtos.SaleDtos.PosConvertRequest;
import com.aromatica.modules.pos.sale.dtos.SaleDtos.PosShelfRequest;
import com.aromatica.modules.pos.sale.dtos.SaleDtos.PosSplitWriteOffRequest;
import com.aromatica.modules.pos.sale.dtos.SaleDtos.ReceiptRequest;
import com.aromatica.modules.pos.sale.dtos.SaleDtos.ReturnRequest;
import com.aromatica.modules.pos.sale.endpoint.SaleEndpoint;
import com.aromatica.modules.pos.sale.mapper.SaleCommandMapper;
import com.aromatica.modules.pos.sale.usecase.SaleReturnUseCase;
import com.aromatica.modules.pos.sale.usecase.SellUseCase;
import com.aromatica.modules.pos.sale.usecase.SplitWriteOffUseCase;
import com.aromatica.modules.pos.sale.usecase.ToShelfUseCase;
import com.aromatica.modules.pos.sale.usecase.VoidReceiptUseCase;
import com.aromatica.modules.pos.shift.model.PosShift;
import com.aromatica.modules.pos.shift.service.PosShiftService;
import com.aromatica.security.auth.authentication.CurrentUser;
import com.aromatica.security.auth.service.UserDirectory;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SaleEndpointImpl implements SaleEndpoint {

    PosShiftService shiftService;
    SellUseCase sell;
    SaleReturnUseCase saleReturn;
    ToShelfUseCase toShelf;
    VoidReceiptUseCase voidReceipt;
    ConvertService convertService;
    ConvertMapper convertMapper;
    SplitWriteOffUseCase splitWriteOff;
    DocMapper docMapper;
    UserDirectory users;
    GiftCertificateService giftService;
    GiftCertificateMapper giftMapper;
    SaleCommandMapper commands;

    @Override
    @Transactional(readOnly = true)
    public List<DocDto> shiftReceipts(UUID shiftId) {
        return docMapper.toDtos(shiftService.shiftReceipts(shiftId, CurrentUser.login()));
    }

    @Override
    @Transactional(readOnly = true)
    public SplitInfo splitInfo(UUID sourceProductId) {
        return convertMapper.toSplitInfo(convertService.splitInfo(sourceProductId));
    }

    /**
     * Склад — из смены, иначе флакон спишется не с той точки; объём — из карточки товара.
     */
    @Override
    @Transactional
    public DocDto convert(
            UUID shiftId,
            PosConvertRequest request
    ) {
        PosShift shift = shiftService.lockOpenShift(shiftId, CurrentUser.login());
        // распив заводит админ в каталоге: созданный на лету остался бы без цены
        if (!convertService.splitInfo(request.sourceProductId()).exists()) {
            throw new BusinessRuleException(
                    "У этого аромата ещё нет распива — заведите его в каталоге с ценой за мл");
        }
        return docMapper.toDto(convertService.open(commands.toConvert(request, shift.getWarehouseId())));
    }

    @Override
    @Transactional
    public GiftCertificateDto sellCertificate(
            UUID shiftId,
            SellRequest request
    ) {
        String login = CurrentUser.login();
        // та же проверка смены, что у чеков
        shiftService.lockOpenShift(shiftId, login);
        return giftMapper.toDto(giftService.sell(request.code(), shiftId,
                request.cashMinor(), request.noCashMinor(),
                request.customerId(), users.idByLogin(login)));
    }

    @Override
    @Transactional
    public DocDto createReceipt(
            UUID shiftId,
            ReceiptRequest request
    ) {
        return docMapper.toDto(sell.handle(shiftId, commands.toSale(request), CurrentUser.login()));
    }

    @Override
    @Transactional
    public DocDto writeOffSplit(
            UUID shiftId,
            PosSplitWriteOffRequest request
    ) {
        return docMapper.toDto(splitWriteOff.handle(shiftId, commands.toWriteOff(request), CurrentUser.login()));
    }

    @Override
    @Transactional
    public DocDto createReturn(
            UUID shiftId,
            ReturnRequest request
    ) {
        return docMapper.toDto(saleReturn.handle(shiftId, commands.toReturn(request), CurrentUser.login()));
    }

    @Override
    @Transactional
    public DocDto toShelf(
            UUID shiftId,
            PosShelfRequest request
    ) {
        return docMapper.toDto(toShelf.handle(shiftId, commands.toShelf(request), CurrentUser.login()));
    }

    @Override
    @Transactional
    public void voidReceipt(
            UUID shiftId,
            UUID docId
    ) {
        voidReceipt.handle(shiftId, docId, CurrentUser.login());
    }
}
