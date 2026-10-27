package com.aromatica.modules.finance.money;

import com.aromatica.modules.finance.money.model.Debt;
import com.aromatica.modules.finance.money.model.MoneyAccount;
import com.aromatica.modules.finance.money.model.MoneyMovement;
import com.aromatica.modules.finance.money.repository.MoneyAccountRepository;
import com.aromatica.modules.finance.money.repository.MoneyMovementRepository;
import com.aromatica.modules.finance.money.service.impl.MoneyServiceImpl;
import com.aromatica.modules.inventory.doc.model.DocStatus;
import com.aromatica.modules.inventory.doc.model.DocType;
import com.aromatica.modules.inventory.doc.model.StockDoc;
import com.aromatica.modules.inventory.doc.service.DocService.SupplierDebt;
import com.aromatica.modules.inventory.doc.service.DocService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Расчёты с поставщиками: знак оплаты и сборка долгов. */
@ExtendWith(MockitoExtension.class)
class MoneyServiceTest {

    @Mock
    private MoneyAccountRepository accountRepository;
    @Mock
    private MoneyMovementRepository movementRepository;
    @Mock
    private DocService docService;

    private MoneyServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new MoneyServiceImpl(accountRepository, movementRepository, docService,
                Clock.systemDefaultZone());
    }

    private static StockDoc postedDoc(
            DocType type,
            UUID counterpartyId,
            long sumMinor,
            LocalDateTime moment
    ) {
        StockDoc doc = new StockDoc();
        doc.setId(UUID.randomUUID());
        doc.setDocType(type);
        doc.setStatus(DocStatus.POSTED);
        doc.setCounterpartyId(counterpartyId);
        doc.setSumMinor(sumMinor);
        doc.setMoment(moment);
        return doc;
    }

    /** За приёмку платим мы, за возврат поставщику — он: знак один и тот же при проведении и оплате задним числом. */
    @Test
    void supplierPayment_signDependsOnDirection() {
        MoneyAccount account = new MoneyAccount();
        account.setId(UUID.randomUUID());
        when(accountRepository.require(eq(account.getId()), anyString())).thenReturn(account);
        StockDoc supply = postedDoc(DocType.SUPPLY, null, 500_00, LocalDateTime.now());
        StockDoc returned = postedDoc(DocType.RETURN_SUPPLY, null, 200_00, LocalDateTime.now());
        returned.setPayAccountId(account.getId());

        service.payDoc(supply, account.getId());
        service.onPost(returned);

        ArgumentCaptor<MoneyMovement> saved = ArgumentCaptor.forClass(MoneyMovement.class);
        verify(movementRepository, times(2)).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(MoneyMovement::getAmountMinor).containsExactly(-500_00L, 200_00L);
        assertThat(saved.getAllValues()).extracting(MoneyMovement::getDocId)
                .containsExactly(supply.getId(), returned.getId());
    }

    @Test
    void debts_biggestFirst_withDocsOfEachSupplier() {
        UUID small = UUID.randomUUID();
        UUID big = UUID.randomUUID();
        when(docService.supplierDebts()).thenReturn(List.of(
                new SupplierDebt(small, 100_00), new SupplierDebt(null, 50_00), new SupplierDebt(big, 900_00)));
        LocalDateTime now = LocalDateTime.now();
        StockDoc bigSupply = postedDoc(DocType.SUPPLY, big, 1000_00, now);
        StockDoc bigReturn = postedDoc(DocType.RETURN_SUPPLY, big, 100_00, now.minusDays(1));
        StockDoc smallSupply = postedDoc(DocType.SUPPLY, small, 100_00, now);
        StockDoc orphan = postedDoc(DocType.SUPPLY, null, 50_00, now);
        when(docService.findUnpaidSupplierDocs(List.of(small, big), true))
                .thenReturn(List.of(bigSupply, smallSupply, orphan, bigReturn));

        List<Debt> debts = service.debts();

        assertThat(debts).extracting(Debt::counterpartyId).containsExactly(big, small, null);
        assertThat(debts.getFirst().debtMinor()).isEqualTo(900_00);
        assertThat(debts.getFirst().docs()).extracting(StockDoc::getId).containsExactly(bigSupply.getId(), bigReturn.getId());
        assertThat(debts.get(2).docs()).extracting(StockDoc::getId).containsExactly(orphan.getId());
    }
}
