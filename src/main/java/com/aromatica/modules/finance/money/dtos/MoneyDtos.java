package com.aromatica.modules.finance.money.dtos;

import com.aromatica.modules.finance.money.model.MoneyAccount;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Суммы в минорных единицах. */
public final class MoneyDtos {

    private MoneyDtos() {}

    public record AccountDto(
            UUID id,
            String name,
            MoneyAccount.Type type,
            boolean isDefault,
            long balanceMinor
    ) {}

    public record MoneyMovementDto(
            long id,
            UUID accountId,
            String accountName,
            UUID docId,
            String docLabel,
            long amountMinor,
            LocalDateTime moment,
            String comment
    ) {}

    public record ManualMovementRequest(

            @NotNull(message = "Не указан счёт")
            UUID accountId,

            long amountMinor,

            LocalDateTime moment,

            String comment
    ) {}

    public record TransferRequest(

            @NotNull(message = "Укажите оба счёта")
            UUID fromAccountId,

            @NotNull(message = "Укажите оба счёта")
            UUID toAccountId,

            @Positive(message = "Сумма перевода должна быть больше нуля")
            long amountMinor,

            String comment
    ) {}

    /** С какого счёта оплачена проведённая приёмка или возврат поставщику. */
    public record PayDocRequest(

            @NotNull(message = "Не указан счёт оплаты")
            UUID accountId
    ) {}

    public record DebtDoc(
            UUID id,
            String docType,
            long number,
            LocalDateTime moment,
            long sumMinor
    ) {}

    public record DebtRow(
            UUID counterpartyId,
            String counterpartyName,
            long debtMinor,
            List<DebtDoc> docs
    ) {}
}
