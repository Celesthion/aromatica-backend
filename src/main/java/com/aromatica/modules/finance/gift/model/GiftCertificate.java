package com.aromatica.modules.finance.gift.model;

import com.aromatica.common.audit.Auditable;
import com.aromatica.common.error.BusinessRuleException;
import com.aromatica.common.error.ConflictException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Бумажный бланк с номиналом — обязательство магазина, а не товар.
 * Непроданный (NEW) — просто бумага; проданный гасится по частям до нуля (USED).
 */
@Entity
@Table(name = "gift_certificate")
@Getter
@Setter
@NoArgsConstructor
public class GiftCertificate extends Auditable {

    /** BLOCKED — бланк потерян или испорчен: ни продать, ни погасить. */
    public enum Status { NEW, ACTIVE, USED, BLOCKED }

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(name = "nominal_minor", nullable = false)
    private long nominalMinor;

    @Column(name = "balance_minor", nullable = false)
    private long balanceMinor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.NEW;

    @Column(columnDefinition = "text")
    private String comment;

    @Column(name = "sold_at")
    private LocalDateTime soldAt;

    /** Деньги за бланк попадают в X-отчёт и пересчёт кассы этой смены. */
    @Column(name = "sold_shift_id")
    private UUID soldShiftId;

    @Column(name = "sold_by")
    private UUID soldBy;

    /** Покупатель, а не предъявитель: нужен, чтобы подтвердить покупку, если бланк потеряют. */
    @Column(name = "buyer_id")
    private UUID buyerId;

    /** Аванс, в выручку смены не идёт. */
    @Column(name = "sold_cash_minor", nullable = false)
    private long soldCashMinor;

    @Column(name = "sold_no_cash_minor", nullable = false)
    private long soldNoCashMinor;

    public long spendableMinor() {
        return status == Status.NEW ? 0 : balanceMinor;
    }

    public static GiftCertificate blank(
            UUID id,
            String code,
            long nominalMinor,
            String comment,
            UUID createdBy
    ) {
        GiftCertificate c = new GiftCertificate();
        c.id = id;
        c.code = code;
        c.nominalMinor = nominalMinor;
        c.balanceMinor = nominalMinor;
        c.status = Status.NEW;
        c.comment = comment;
        c.setCreatedBy(createdBy);
        return c;
    }

    /** Только за полный номинал. */
    public void sell(
            long cashMinor,
            long noCashMinor,
            UUID shiftId,
            UUID soldBy,
            UUID buyerId,
            LocalDateTime soldAt
    ) {
        if (status == Status.BLOCKED) {
            throw new ConflictException("Бланк заблокирован — продать нельзя");
        }
        if (status != Status.NEW) {
            throw new ConflictException("Этот сертификат уже продан " + this.soldAt);
        }
        long paid = cashMinor + noCashMinor;
        if (paid != nominalMinor) {
            throw new ConflictException("Оплата (" + paid / 100
                    + ") не сходится с номиналом сертификата (" + nominalMinor / 100 + ")");
        }
        status = Status.ACTIVE;
        balanceMinor = nominalMinor;
        this.soldAt = soldAt;
        soldShiftId = shiftId;
        this.soldBy = soldBy;
        soldCashMinor = cashMinor;
        soldNoCashMinor = noCashMinor;
        this.buyerId = buyerId;
    }

    /** Сдачу деньгами не выдаём: больше остатка не списать. */
    public void redeem(long amountMinor) {
        if (status == Status.NEW) {
            throw new ConflictException("Сертификат ещё не продан");
        }
        if (status == Status.BLOCKED) {
            throw new ConflictException("Сертификат заблокирован");
        }
        if (amountMinor <= 0) {
            throw new BusinessRuleException("Сумма погашения должна быть больше нуля");
        }
        if (amountMinor > balanceMinor) {
            throw new ConflictException("На сертификате осталось " + balanceMinor / 100 + " — этого не хватает");
        }
        balanceMinor -= amountMinor;
        status = balanceMinor == 0 ? Status.USED : Status.ACTIVE;
    }

    /**
     * Не выше номинала, иначе через возвраты делали бы деньги.
     * Блокировку не снимает: потерянный бланк разблокирует только человек.
     */
    public void refund(long amountMinor) {
        if (balanceMinor + amountMinor > nominalMinor) {
            throw new ConflictException("Возврат больше, чем было погашено с сертификата");
        }
        balanceMinor += amountMinor;
        if (status != Status.BLOCKED) {
            status = Status.ACTIVE;
        }
    }

    public void block(
            String comment,
            UUID blockedBy,
            LocalDateTime at
    ) {
        status = Status.BLOCKED;
        this.comment = comment;
        setUpdatedBy(blockedBy);
        setUpdatedAt(at);
    }
}
