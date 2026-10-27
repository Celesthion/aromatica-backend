package com.aromatica.modules.finance.gift.service.impl;

import com.aromatica.common.error.BusinessRuleException;
import com.aromatica.common.error.ConflictException;
import com.aromatica.common.error.NotFoundException;
import com.aromatica.modules.finance.gift.model.GiftCertificate.Status;
import com.aromatica.modules.finance.gift.model.GiftCertificate;
import com.aromatica.modules.finance.gift.model.GiftCertificateUse;
import com.aromatica.modules.finance.gift.repository.GiftCertificateRepository;
import com.aromatica.modules.finance.gift.repository.GiftCertificateUseRepository;
import com.aromatica.modules.finance.gift.service.GiftCertificateService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class GiftCertificateServiceImpl implements GiftCertificateService {

    GiftCertificateRepository repository;
    GiftCertificateUseRepository useRepository;
    /** Погашение вызывает проведение, где userId не передают. */
    AuditorAware<UUID> auditor;
    EntityManager entityManager;
    Clock clock;

    @Override
    @Transactional
    public List<GiftCertificate> importBatch(
            List<String> codes,
            long nominalMinor,
            String comment,
            UUID userId
    ) {
        // дубли внутри пачки схлопываем с сохранением порядка: это опечатка, а не два бланка
        List<String> clean = new ArrayList<>(new LinkedHashSet<>(codes.stream()
                .map(c -> c == null ? "" : c.trim())
                .filter(c -> !c.isBlank())
                .toList()));
        if (clean.isEmpty()) {
            throw new BusinessRuleException("Не указано ни одного кода");
        }

        // отказываем целиком: с половиной пачки не понять, какие бланки завелись
        List<GiftCertificate> existing = repository.findByCodeIn(clean);
        if (!existing.isEmpty()) {
            throw new BusinessRuleException("Эти коды уже заведены: "
                    + existing.stream().map(GiftCertificate::getCode).limit(5).toList()
                    + (existing.size() > 5 ? " и ещё " + (existing.size() - 5) : ""));
        }

        List<GiftCertificate> batch = clean.stream()
                .map(code -> GiftCertificate.blank(UUID.randomUUID(), code, nominalMinor, comment, userId))
                .toList();
        return repository.saveAll(batch);
    }

    @Override
    @Transactional
    public List<GiftCertificate> generateBatch(
            int count,
            long nominalMinor,
            String comment,
            UUID userId
    ) {
        if (count < 1) {
            throw new BusinessRuleException("Сколько бланков — минимум 1");
        }
        // совпавшие с заведёнными коды перегенерируем
        List<String> codes = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        while (codes.size() < count) {
            String code = GiftCodes.next();
            if (seen.add(code)) codes.add(code);
        }
        for (int attempt = 0; attempt < 10; attempt++) {
            Set<String> taken = repository.findByCodeIn(codes).stream()
                    .map(GiftCertificate::getCode).collect(Collectors.toSet());
            if (taken.isEmpty()) {
                return importBatch(codes, nominalMinor, comment, userId);
            }
            for (int i = 0; i < codes.size(); i++) {
                if (taken.contains(codes.get(i))) codes.set(i, GiftCodes.next());
            }
        }
        throw new ConflictException("Не удалось подобрать свободные коды — повторите");
    }

    @Override
    public Optional<GiftCertificate> find(String code) {
        // с бланка код вводят группами через пробел, в базе он слитно
        return repository.findByCode(GiftCodes.normalize(code));
    }

    @Override
    public GiftCertificate byCode(String code) {
        return find(code)
                .orElseThrow(() -> new NotFoundException("Сертификат не найден: " + code));
    }

    @Override
    @Transactional
    public GiftCertificate sell(
            String code,
            UUID shiftId,
            long cashMinor,
            long noCashMinor,
            UUID buyerId,
            UUID userId
    ) {
        // от двойного нажатия «Продать»
        GiftCertificate c = lockFresh(byCode(code));
        c.sell(cashMinor, noCashMinor, shiftId, userId, buyerId, LocalDateTime.now(clock));
        return c;
    }

    /**
     * Блокирует и перечитывает бланк: чек загрузил его раньше, и запрос с блокировкой вернул бы
     * объект из сессии со старым остатком — два параллельных чека списали бы номинал дважды.
     */
    private GiftCertificate lockFresh(GiftCertificate c) {
        entityManager.refresh(c, LockModeType.PESSIMISTIC_WRITE);
        return c;
    }

    private GiftCertificate lockedById(UUID id) {
        return lockFresh(repository.require(id, "Сертификат не найден"));
    }

    @Override
    @Transactional
    public GiftCertificate redeemForDoc(
            UUID certificateId,
            long amountMinor,
            UUID docId
    ) {
        GiftCertificate c = lockedById(certificateId);
        c.redeem(amountMinor);
        writeUse(c.getId(), docId, -amountMinor, currentUserId());
        return c;
    }

    @Override
    @Transactional
    public void revertForDoc(UUID docId) {
        // журнал не гарантирует один бланк на чек
        Map<UUID, Long> netByCertificate = new LinkedHashMap<>();
        for (GiftCertificateUse use : useRepository.findByDocIdIn(List.of(docId))) {
            netByCertificate.merge(use.getCertificateId(), use.getAmountMinor(), Long::sum);
        }
        for (Map.Entry<UUID, Long> e : netByCertificate.entrySet()) {
            // минус в журнале — списано и ещё не возвращено; ноль — откат уже сделан
            long owed = -e.getValue();
            if (owed > 0) {
                refund(e.getKey(), owed, docId);
            }
        }
    }

    private void refund(
            UUID certificateId,
            long amountMinor,
            UUID docId
    ) {
        // под блокировкой, как погашение: иначе перетрут остаток друг друга
        GiftCertificate c = lockedById(certificateId);
        c.refund(amountMinor);
        writeUse(c.getId(), docId, amountMinor, currentUserId());
    }

    /** У системных операций пусто, как в аудите. */
    private UUID currentUserId() {
        return auditor.getCurrentAuditor().orElse(null);
    }

    @Override
    @Transactional
    public GiftCertificate block(
            UUID id,
            String comment,
            UUID userId
    ) {
        GiftCertificate c = lockedById(id);
        c.block(comment, userId, LocalDateTime.now(clock));
        return c;
    }

    @Override
    public Stats stats() {
        return new Stats(repository.sumBalanceByStatus(Status.ACTIVE),
                repository.countByStatus(Status.NEW),
                repository.count());
    }

    @Override
    public List<GiftCertificateUse> history(UUID certificateId) {
        return useRepository.findByCertificateIdOrderByAtDesc(certificateId);
    }

    @Override
    public List<GiftCertificate> soldInShift(UUID shiftId) {
        return repository.findBySoldShiftId(shiftId);
    }

    @Override
    public long redeemedForDocs(List<UUID> docIds) {
        if (docIds.isEmpty()) {
            return 0;
        }
        // журнал знаковый: погашение минусом, возврат плюсом
        return -useRepository.findByDocIdIn(docIds).stream()
                .mapToLong(GiftCertificateUse::getAmountMinor)
                .sum();
    }

    private void writeUse(
            UUID certificateId,
            UUID docId,
            long amountMinor,
            UUID userId
    ) {
        GiftCertificateUse use = new GiftCertificateUse();
        use.setCertificateId(certificateId);
        use.setDocId(docId);
        use.setAmountMinor(amountMinor);
        use.setCreatedBy(userId);
        useRepository.save(use);
    }
}
