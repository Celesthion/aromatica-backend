package com.aromatica.modules.finance.money.service.impl;

import com.aromatica.common.error.BusinessRuleException;
import com.aromatica.common.error.ConflictException;
import com.aromatica.modules.finance.money.model.Debt;
import com.aromatica.modules.finance.money.model.MoneyAccount;
import com.aromatica.modules.finance.money.model.MoneyMovement;
import com.aromatica.modules.finance.money.repository.MoneyAccountRepository;
import com.aromatica.modules.finance.money.repository.MoneyMovementRepository;
import com.aromatica.modules.finance.money.service.MoneyService;
import com.aromatica.modules.inventory.doc.model.DocType;
import com.aromatica.modules.inventory.doc.model.StockDoc;
import com.aromatica.modules.inventory.doc.service.DocService.SupplierDebt;
import com.aromatica.modules.inventory.doc.service.DocService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MoneyServiceImpl implements MoneyService {

    MoneyAccountRepository accountRepository;
    MoneyMovementRepository movementRepository;
    DocService docService;
    Clock clock;

    @Override
    public List<MoneyAccount> activeAccounts() {
        return accountRepository.findByArchivedFalseOrderByCreatedAtAsc();
    }

    @Override
    public Map<UUID, Long> balancesByAccount() {
        Map<UUID, Long> balances = new HashMap<>();
        movementRepository.balancesByAccount()
                .forEach(row -> balances.put(row.getAccountId(), row.getBalanceMinor()));
        return balances;
    }

    @Override
    public Map<UUID, String> accountNames() {
        Map<UUID, String> names = new HashMap<>();
        accountRepository.findAll().forEach(a -> names.put(a.getId(), a.getName()));
        return names;
    }

    @Override
    public MoneyAccount findAccount(UUID id) {
        return accountRepository.require(id, "Счёт не найден");
    }

    @Override
    public Slice<MoneyMovement> movements(
            UUID accountId,
            int page,
            int size
    ) {
        PageRequest pageable = PageRequest.of(page, size);
        return accountId == null
                ? movementRepository.findAllByOrderByMomentDescIdDesc(pageable)
                : movementRepository.findByAccountIdOrderByMomentDescIdDesc(accountId, pageable);
    }

    @Transactional
    @Override
    public MoneyMovement createManual(
            UUID accountId,
            long amountMinor,
            LocalDateTime moment,
            String comment
    ) {
        if (amountMinor == 0) {
            throw new BusinessRuleException("Сумма не может быть нулевой");
        }
        MoneyAccount account = findAccount(accountId);
        return movementRepository.save(MoneyMovement.manual(account.getId(), amountMinor,
                moment != null ? moment : LocalDateTime.now(clock), comment));
    }

    @Transactional
    @Override
    public void deleteManual(long id) {
        MoneyMovement m = movementRepository.require(id, "Движение не найдено");
        if (m.getDocId() != null) {
            throw new ConflictException(
                    "Это автодвижение из документа — распроведите документ вместо удаления");
        }
        movementRepository.delete(m);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    @Override
    public void onPost(StockDoc doc) {
        switch (doc.getDocType()) {
            case SALE -> postSplitMoney(doc, +1, "Продажа №" + doc.getNumber());
            case RETURN -> postSplitMoney(doc, -1, "Возврат №" + doc.getNumber());
            case SUPPLY, RETURN_SUPPLY -> {
                // неоплаченную приёмку оплатят позже через payDoc, той же проводкой
                if (doc.getPayAccountId() != null) {
                    postSupplierPayment(doc);
                }
            }
            default -> { }
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    @Override
    public void onUnpost(StockDoc doc) {
        movementRepository.deleteByDocId(doc.getId());
    }

    @Transactional
    @Override
    public void transfer(
            UUID fromAccountId,
            UUID toAccountId,
            long amountMinor,
            String comment
    ) {
        if (fromAccountId.equals(toAccountId)) {
            throw new BusinessRuleException("Счета перевода должны быть разными");
        }
        MoneyAccount from = accountRepository.require(fromAccountId, "Счёт-источник не найден");
        MoneyAccount to = accountRepository.require(toAccountId, "Счёт-получатель не найден");
        String text = "Перевод: " + from.getName() + " → " + to.getName()
                + (comment != null && !comment.isBlank() ? " · " + comment : "");
        LocalDateTime now = LocalDateTime.now(clock);
        movementRepository.save(MoneyMovement.manual(from.getId(), -amountMinor, now, text));
        movementRepository.save(MoneyMovement.manual(to.getId(), amountMinor, now, text));
    }

    @Transactional
    @Override
    public void manualAdjustment(
            MoneyAccount.Type type,
            long amountMinor,
            String comment
    ) {
        if (amountMinor == 0) {
            return;
        }
        movementRepository.save(MoneyMovement.manual(accountOf(type), amountMinor, LocalDateTime.now(clock), comment));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    @Override
    public void payDoc(
            StockDoc doc,
            UUID accountId
    ) {
        if (!doc.isPosted()) {
            throw new ConflictException("Оплатить можно только проведённый документ");
        }
        if (doc.getDocType() != DocType.SUPPLY && doc.getDocType() != DocType.RETURN_SUPPLY) {
            throw new ConflictException("Оплата задним числом — только для приёмок и возвратов поставщику");
        }
        if (doc.getPayAccountId() != null) {
            throw new ConflictException("Документ уже оплачен");
        }
        MoneyAccount account = accountRepository.require(accountId, "Счёт не найден");
        doc.setPayAccountId(account.getId());
        postSupplierPayment(doc);
    }

    /** Одна проводка и при проведении, и при оплате задним числом. */
    private void postSupplierPayment(StockDoc doc) {
        boolean supply = doc.getDocType() == DocType.SUPPLY;
        write(doc, doc.getPayAccountId(), supply ? -doc.getSumMinor() : doc.getSumMinor(),
                (supply ? "Оплата приёмки №" : "Возврат поставщику №") + doc.getNumber());
    }

    @Override
    public List<Debt> debts() {
        List<SupplierDebt> debts = docService.supplierDebts();
        List<UUID> counterpartyIds = debts.stream().map(SupplierDebt::counterpartyId).filter(Objects::nonNull).toList();
        boolean withoutCounterparty = debts.stream().anyMatch(d -> d.counterpartyId() == null);
        Map<UUID, List<StockDoc>> docs = new HashMap<>();
        docService.findUnpaidSupplierDocs(counterpartyIds, withoutCounterparty).forEach(d ->
                docs.computeIfAbsent(d.getCounterpartyId(), k -> new ArrayList<>()).add(d));
        return debts.stream()
                .sorted(Comparator.comparingLong(SupplierDebt::debtMinor).reversed())
                .map(d -> new Debt(d.counterpartyId(), d.debtMinor(), docs.getOrDefault(d.counterpartyId(), List.of())))
                .toList();
    }

    /** sign = +1 продажа, −1 возврат. */
    private void postSplitMoney(
            StockDoc doc,
            int sign,
            String comment
    ) {
        // сертификат не движение денег: деньги пришли при продаже бланка
        if (doc.getCashMinor() != 0) {
            write(doc, accountOf(MoneyAccount.Type.CASH), sign * doc.getCashMinor(), comment);
        }
        if (doc.getNoCashMinor() != 0) {
            write(doc, accountOf(MoneyAccount.Type.BANK), sign * doc.getNoCashMinor(), comment);
        }
    }

    private UUID accountOf(MoneyAccount.Type type) {
        return accountRepository.findFirstByTypeAndArchivedFalseOrderByIsDefaultDescCreatedAtAsc(type)
                .orElseThrow(() -> new ConflictException("Не найден денежный счёт типа " + type))
                .getId();
    }

    private void write(
            StockDoc doc,
            UUID accountId,
            long amountMinor,
            String comment
    ) {
        movementRepository.save(MoneyMovement.ofDoc(doc.getId(), accountId, amountMinor, doc.getMoment(), comment));
    }
}
