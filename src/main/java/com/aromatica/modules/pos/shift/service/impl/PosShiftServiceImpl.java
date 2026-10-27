package com.aromatica.modules.pos.shift.service.impl;

import com.aromatica.common.error.ConflictException;
import com.aromatica.modules.finance.money.model.MoneyAccount;
import com.aromatica.modules.finance.money.service.MoneyService;
import com.aromatica.modules.inventory.doc.model.StockDoc;
import com.aromatica.modules.inventory.doc.service.DocService;
import com.aromatica.modules.inventory.warehouse.service.WarehouseLookup;
import com.aromatica.modules.pos.shift.event.ShiftClosedEvent;
import com.aromatica.modules.pos.shift.event.ShiftOpenedEvent;
import com.aromatica.modules.pos.shift.model.PosShift;
import com.aromatica.modules.pos.shift.model.ShiftClosing;
import com.aromatica.modules.pos.shift.model.ShiftOpening;
import com.aromatica.modules.pos.shift.repository.PosShiftRepository;
import com.aromatica.modules.pos.shift.service.PosShiftService;
import com.aromatica.modules.pos.shift.service.ShiftSummaryQueryService;
import com.aromatica.security.auth.model.AppUser;
import com.aromatica.security.auth.service.UserDirectory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PosShiftServiceImpl implements PosShiftService {

    PosShiftRepository shiftRepository;
    DocService docService;
    MoneyService moneyService;
    ApplicationEventPublisher events;
    UserDirectory users;
    WarehouseLookup warehouseLookup;
    /** Расчётный нал на закрытие — из той же сводки, что видит кассир. */
    ShiftSummaryQueryService summaryQuery;
    EntityManager entityManager;
    Clock clock;

    @Transactional
    @Override
    public PosShift openShift(
            ShiftOpening opening,
            String login
    ) {
        UUID userId = requireUserId(login);
        UUID warehouseId = opening.warehouseId() != null ? opening.warehouseId()
                : warehouseLookup.defaultWarehouseId();
        // один человек — одна касса, даже на другом складе
        shiftRepository.findFirstByOpenedByAndStatus(userId, PosShift.Status.OPEN)
                .ifPresent(s -> {
                    throw new ConflictException("У вас уже открыта смена №" + s.getNumber()
                            + " — сначала закройте её");
                });
        shiftRepository.findFirstByWarehouseIdAndStatus(warehouseId, PosShift.Status.OPEN)
                .ifPresent(s -> {
                    throw new ConflictException("Смена №" + s.getNumber() + " уже открыта — сначала закройте её");
                });
        PosShift shift = PosShift.open(UUID.randomUUID(), shiftRepository.nextNumber(), warehouseId,
                userId, LocalDateTime.now(clock), opening.openingCashMinor());
        PosShift saved = shiftRepository.save(shift);
        events.publishEvent(new ShiftOpenedEvent(saved));
        return saved;
    }

    @Transactional
    @Override
    public PosShift closeShift(
            UUID shiftId,
            ShiftClosing closing,
            String login
    ) {
        PosShift shift = getShift(shiftId);
        // эксклюзивная блокировка: закрытие ждёт чеки, которые уже начались, и считает итог
        // вместе с ними, а чек, пришедший после, увидит смену закрытой (см. lockOpenShift)
        entityManager.refresh(shift, LockModeType.PESSIMISTIC_WRITE);
        shift.requireOpen();
        shift.close(requireUserId(login), LocalDateTime.now(clock), summaryQuery.expectedCashMinor(shift),
                closing.countedCashMinor(), closing.comment());
        // недостачу или излишек проводим корректировкой счёта наличных
        if (closing.countedCashMinor() != null) {
            long diff = closing.countedCashMinor() - shift.getExpectedCashMinor();
            moneyService.manualAdjustment(MoneyAccount.Type.CASH, diff,
                    (diff < 0 ? "Недостача" : "Излишек") + " по смене №" + shift.getNumber());
        }
        events.publishEvent(new ShiftClosedEvent(shift));
        return shift;
    }

    @Override
    public Optional<PosShift> currentShift(String login) {
        return shiftRepository.findByStatusOrderByOpenedAtDesc(PosShift.Status.OPEN).stream()
                .filter(accessibleShift(login))
                .findFirst();
    }

    @Override
    public List<PosShift> openShifts(String login) {
        return shiftRepository.findByStatusOrderByOpenedAtDesc(PosShift.Status.OPEN).stream()
                .filter(accessibleShift(login))
                .toList();
    }

    /** Админу доступны все склады. */
    private Predicate<PosShift> accessibleShift(String login) {
        AppUser user = requireUser(login);
        return s -> user.canAccessWarehouse(s.getWarehouseId());
    }

    @Override
    public List<StockDoc> shiftReceipts(
            UUID shiftId,
            String login
    ) {
        requireAccessibleShift(shiftId, login);
        return docService.findPostedReceiptsOfShift(shiftId);
    }

    private void requireShiftAccess(
            PosShift shift,
            String login
    ) {
        if (!requireUser(login).canAccessWarehouse(shift.getWarehouseId())) {
            throw new AccessDeniedException(
                    "Нет доступа к складу этой смены — обратитесь к администратору");
        }
    }

    @Override
    public PosShift requireAccessibleShift(
            UUID shiftId,
            String login
    ) {
        PosShift shift = getShift(shiftId);
        requireShiftAccess(shift, login);
        return shift;
    }

    @Override
    public PosShift requireAccessibleOpenShift(
            UUID shiftId,
            String login
    ) {
        PosShift shift = openShiftById(shiftId);
        requireShiftAccess(shift, login);
        return shift;
    }

    @Transactional
    @Override
    public PosShift lockOpenShift(
            UUID shiftId,
            String login
    ) {
        PosShift shift = getShift(shiftId);
        // FOR SHARE: чеки смены друг другу не мешают, а закрытие с эксклюзивной их дождётся;
        // refresh заодно перечитывает статус
        entityManager.refresh(shift, LockModeType.PESSIMISTIC_READ);
        shift.requireOpen();
        requireShiftAccess(shift, login);
        return shift;
    }

    private PosShift openShiftById(UUID shiftId) {
        PosShift shift = getShift(shiftId);
        shift.requireOpen();
        return shift;
    }

    private PosShift getShift(UUID shiftId) {
        return shiftRepository.require(shiftId, "Смена не найдена");
    }

    /** Для проверок доступа: залогиненный пользователь существует всегда, кроме гонок. */
    private AppUser requireUser(String login) {
        return users.require(login);
    }

    private UUID requireUserId(String login) {
        return requireUser(login).getId();
    }
}
