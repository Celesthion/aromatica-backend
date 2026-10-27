package com.aromatica.modules.finance.money.mapper;

import com.aromatica.modules.finance.money.dtos.MoneyDtos.AccountDto;
import com.aromatica.modules.finance.money.dtos.MoneyDtos.DebtDoc;
import com.aromatica.modules.finance.money.dtos.MoneyDtos.DebtRow;
import com.aromatica.modules.finance.money.dtos.MoneyDtos.MoneyMovementDto;
import com.aromatica.modules.finance.money.model.Debt;
import com.aromatica.modules.finance.money.model.MoneyAccount;
import com.aromatica.modules.finance.money.model.MoneyMovement;
import com.aromatica.modules.finance.money.service.MoneyService;
import com.aromatica.modules.inventory.doc.model.StockDoc;
import com.aromatica.modules.inventory.doc.service.DocService;
import com.aromatica.modules.masterdata.party.service.CounterpartyService;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Остатки, названия счетов, подписи документов и имена поставщиков — вне сущностей, грузим пачкой. */
@Mapper
public abstract class MoneyMapper {

    // абстрактный маппер MapStruct создаёт сам, поэтому внедрение — полями
    @Autowired
    protected MoneyService moneyService;
    @Autowired
    protected DocService docService;
    @Autowired
    protected CounterpartyService counterpartyService;

    public List<AccountDto> toAccountDtos(List<MoneyAccount> accounts) {
        Map<UUID, Long> balances = moneyService.balancesByAccount();
        return accounts.stream().map(a -> toDto(a, balances.getOrDefault(a.getId(), 0L))).toList();
    }

    public MoneyMovementDto toDto(MoneyMovement movement) {
        return toMovementDtos(List.of(movement)).getFirst();
    }

    public List<MoneyMovementDto> toMovementDtos(List<MoneyMovement> movements) {
        Map<UUID, String> accountNames = moneyService.accountNames();
        List<UUID> docIds = movements.stream()
                .map(MoneyMovement::getDocId).filter(Objects::nonNull).distinct().toList();
        Map<UUID, String> docLabels = new HashMap<>();
        if (!docIds.isEmpty()) {
            docService.findAllById(docIds).forEach(d -> docLabels.put(d.getId(), docLabel(d)));
        }
        return movements.stream()
                .map(m -> toDto(m, accountNames.getOrDefault(m.getAccountId(), "?"),
                        m.getDocId() == null ? null : docLabels.get(m.getDocId())))
                .toList();
    }

    public List<DebtRow> toDebtRows(List<Debt> debts) {
        Map<UUID, String> names = counterpartyService.names(debts.stream()
                .map(Debt::counterpartyId).filter(Objects::nonNull).toList());
        return debts.stream()
                .map(d -> new DebtRow(d.counterpartyId(),
                        d.counterpartyId() == null ? "Без контрагента" : names.getOrDefault(d.counterpartyId(), "?"),
                        d.debtMinor(), d.docs().stream().map(this::toDebtDoc).toList()))
                .toList();
    }

    @Mapping(target = "isDefault", source = "account.default")
    protected abstract AccountDto toDto(MoneyAccount account, long balanceMinor);

    @Mapping(target = "accountName", source = "accountName")
    @Mapping(target = "docLabel", source = "docLabel")
    protected abstract MoneyMovementDto toDto(MoneyMovement movement, String accountName, String docLabel);

    protected abstract DebtDoc toDebtDoc(StockDoc doc);

    private static String docLabel(StockDoc d) {
        return switch (d.getDocType()) {
            case SALE -> "Продажа №" + d.getNumber();
            case SUPPLY -> "Приёмка №" + d.getNumber();
            case RETURN -> "Возврат №" + d.getNumber();
            case RETURN_SUPPLY -> "Возврат поставщику №" + d.getNumber();
            default -> d.getDocType().name() + " №" + d.getNumber();
        };
    }
}
