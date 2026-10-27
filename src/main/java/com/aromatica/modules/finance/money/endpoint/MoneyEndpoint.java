package com.aromatica.modules.finance.money.endpoint;

import com.aromatica.common.search.SliceResponse;
import com.aromatica.modules.finance.money.dtos.MoneyDtos.AccountDto;
import com.aromatica.modules.finance.money.dtos.MoneyDtos.DebtRow;
import com.aromatica.modules.finance.money.dtos.MoneyDtos.ManualMovementRequest;
import com.aromatica.modules.finance.money.dtos.MoneyDtos.MoneyMovementDto;
import com.aromatica.modules.finance.money.dtos.MoneyDtos.TransferRequest;
import com.aromatica.modules.inventory.doc.dtos.DocDtos.DocDto;

import java.util.List;
import java.util.UUID;

public interface MoneyEndpoint {

    List<AccountDto> accounts();

    SliceResponse<MoneyMovementDto> movements(UUID accountId, int page, int size);

    MoneyMovementDto createManual(ManualMovementRequest request);

    void transfer(TransferRequest request);

    List<DebtRow> debts();

    void deleteManual(long id);

    /** Живёт здесь, а не в документах: это движение денег, документ в нём только предмет. */
    DocDto payDoc(UUID docId, UUID accountId);
}
