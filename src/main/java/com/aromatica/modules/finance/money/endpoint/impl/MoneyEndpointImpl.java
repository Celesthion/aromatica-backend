package com.aromatica.modules.finance.money.endpoint.impl;

import com.aromatica.common.search.PageMapper;
import com.aromatica.common.search.SliceResponse;
import com.aromatica.modules.finance.money.dtos.MoneyDtos.AccountDto;
import com.aromatica.modules.finance.money.dtos.MoneyDtos.DebtRow;
import com.aromatica.modules.finance.money.dtos.MoneyDtos.ManualMovementRequest;
import com.aromatica.modules.finance.money.dtos.MoneyDtos.MoneyMovementDto;
import com.aromatica.modules.finance.money.dtos.MoneyDtos.TransferRequest;
import com.aromatica.modules.finance.money.endpoint.MoneyEndpoint;
import com.aromatica.modules.finance.money.mapper.MoneyMapper;
import com.aromatica.modules.finance.money.model.MoneyMovement;
import com.aromatica.modules.finance.money.service.MoneyService;
import com.aromatica.modules.inventory.doc.dtos.DocDtos.DocDto;
import com.aromatica.modules.inventory.doc.mapper.DocMapper;
import com.aromatica.modules.inventory.doc.model.DocEvent;
import com.aromatica.modules.inventory.doc.model.StockDoc;
import com.aromatica.modules.inventory.doc.service.DocEventService;
import com.aromatica.modules.inventory.doc.service.DocService;
import com.aromatica.security.auth.authentication.CurrentUser;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MoneyEndpointImpl implements MoneyEndpoint {

    MoneyService moneyService;
    DocService docService;
    MoneyMapper mapper;
    DocEventService docEventService;
    DocMapper docMapper;
    PageMapper pageMapper;

    @Override
    @Transactional(readOnly = true)
    public List<AccountDto> accounts() {
        return mapper.toAccountDtos(moneyService.activeAccounts());
    }

    @Override
    @Transactional(readOnly = true)
    public SliceResponse<MoneyMovementDto> movements(
            UUID accountId,
            int page,
            int size
    ) {
        Slice<MoneyMovement> movements = moneyService.movements(accountId, page, size);
        return pageMapper.toSlice(movements, mapper.toMovementDtos(movements.getContent()));
    }

    @Override
    @Transactional
    public MoneyMovementDto createManual(ManualMovementRequest request) {
        MoneyMovement m = moneyService.createManual(request.accountId(), request.amountMinor(),
                request.moment(), request.comment());
        return mapper.toDto(m);
    }

    @Override
    @Transactional
    public void transfer(TransferRequest request) {
        moneyService.transfer(request.fromAccountId(), request.toAccountId(),
                request.amountMinor(), request.comment());
    }

    @Override
    @Transactional(readOnly = true)
    public List<DebtRow> debts() {
        return mapper.toDebtRows(moneyService.debts());
    }

    @Override
    @Transactional
    public void deleteManual(long id) {
        moneyService.deleteManual(id);
    }

    @Override
    @Transactional
    public DocDto payDoc(
            UUID docId,
            UUID accountId
    ) {
        // под блокировкой: две параллельные оплаты не должны обе увидеть «не оплачен»
        StockDoc doc = docService.findForUpdate(docId);
        moneyService.payDoc(doc, accountId);
        // в журнале документа остаётся отметка «оплачен», её слушает лента активности
        docEventService.record(docId, DocEvent.Action.PAY, CurrentUser.login());
        return docMapper.toDto(doc);
    }
}
