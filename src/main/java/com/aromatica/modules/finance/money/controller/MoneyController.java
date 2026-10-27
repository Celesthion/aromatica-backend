package com.aromatica.modules.finance.money.controller;

import com.aromatica.common.search.Paging;
import com.aromatica.common.search.SliceResponse;
import com.aromatica.modules.finance.money.dtos.MoneyDtos.AccountDto;
import com.aromatica.modules.finance.money.dtos.MoneyDtos.DebtRow;
import com.aromatica.modules.finance.money.dtos.MoneyDtos.ManualMovementRequest;
import com.aromatica.modules.finance.money.dtos.MoneyDtos.MoneyMovementDto;
import com.aromatica.modules.finance.money.dtos.MoneyDtos.PayDocRequest;
import com.aromatica.modules.finance.money.dtos.MoneyDtos.TransferRequest;
import com.aromatica.modules.finance.money.endpoint.MoneyEndpoint;
import com.aromatica.modules.inventory.doc.dtos.DocDtos.DocDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
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

@Tag(name = "Деньги", description = "Счета с остатками, журнал движений, ручные приход и расход. Только администратор.")
@RestController
@PreAuthorize("hasRole('ADMIN')")
@RequestMapping("/api/admin/money")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MoneyController {

    MoneyEndpoint moneyEndpoint;

    @Operation(summary = "Счета с остатками")
    @GetMapping("/accounts")
    public List<AccountDto> accounts() {
        return moneyEndpoint.accounts();
    }

    @Operation(summary = "Журнал движений")
    @GetMapping("/movements")
    public SliceResponse<MoneyMovementDto> movements(
            @RequestParam(required = false) UUID accountId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        return moneyEndpoint.movements(accountId, Paging.page(page), Paging.size(size));
    }

    @Operation(summary = "Ручной приход или расход", description = "Приход (+) или расход (−) по счёту.")
    @PostMapping("/movements")
    public MoneyMovementDto createManual(@Valid @RequestBody ManualMovementRequest request) {
        return moneyEndpoint.createManual(request);
    }

    @Operation(summary = "Оплатить документ", description = "Оплата проведённой приёмки или возврата поставщику; закрывает долг.")
    @PostMapping("/pay/{docId}")
    public DocDto payDoc(
            @PathVariable UUID docId,
            @Valid @RequestBody PayDocRequest request
    ) {
        return moneyEndpoint.payDoc(docId, request.accountId());
    }

    @Operation(summary = "Перевод между счетами", description = "Например, инкассация: наличные → банк.")
    @PostMapping("/transfer")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void transfer(@Valid @RequestBody TransferRequest request) {
        moneyEndpoint.transfer(request);
    }

    @Operation(summary = "Долги поставщикам", description = "Проведённые неоплаченные приёмки минус возвраты поставщику.")
    @GetMapping("/debts")
    public List<DebtRow> debts() {
        return moneyEndpoint.debts();
    }

    @Operation(summary = "Удалить ручное движение", description = "Удалить можно только ручное движение; автоматические движения удаляются вместе с документом.")
    @DeleteMapping("/movements/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteManual(@PathVariable long id) {
        moneyEndpoint.deleteManual(id);
    }
}
