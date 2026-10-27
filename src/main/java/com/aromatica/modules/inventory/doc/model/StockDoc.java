package com.aromatica.modules.inventory.doc.model;

import com.aromatica.common.audit.Auditable;
import com.aromatica.common.error.ConflictException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Складской документ. Остатки и stock_movement меняет только проведение
 * ({@link com.aromatica.modules.inventory.doc.service.PostingService}).
 */
@Entity
@Table(name = "stock_doc")
@Getter
@Setter
@NoArgsConstructor
public class StockDoc extends Auditable {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "doc_type", nullable = false)
    private DocType docType;

    /** Сквозной номер по типу документа. */
    @Column(nullable = false)
    private long number;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DocStatus status = DocStatus.DRAFT;

    /** Дата документа — можно задним числом, поэтому отдельно от createdAt. */
    @Column(nullable = false)
    private LocalDateTime moment;

    @Column(name = "warehouse_id", nullable = false)
    private UUID warehouseId;

    /** Склад-приёмник; заполняется только у перемещений (MOVE). */
    @Column(name = "warehouse_to_id")
    private UUID warehouseToId;

    @Column(name = "counterparty_id")
    private UUID counterpartyId;

    /** Итог документа в минорных единицах (тыйын). */
    @Column(name = "sum_minor", nullable = false)
    private long sumMinor;

    // Разбивка оплаты чека (SALE): нал / перевод, в минорных единицах.
    @Column(name = "cash_minor", nullable = false)
    private long cashMinor;

    @Column(name = "no_cash_minor", nullable = false)
    private long noCashMinor;

    /** Счёт оплаты приёмки (SUPPLY); NULL — не оплачена (долг поставщику). */
    @Column(name = "pay_account_id")
    private UUID payAccountId;

    /** Кассовая смена, в которой пробит чек (SALE/RETURN с кассы); NULL — не с кассы. */
    @Column(name = "shift_id")
    private UUID shiftId;

    /** Исходный чек продажи для возврата (RETURN → SALE); NULL — не возврат по чеку. */
    @Column(name = "source_doc_id")
    private UUID sourceDocId;

    @Column(columnDefinition = "text")
    private String comment;

    @Column(name = "posted_at")
    private LocalDateTime postedAt;

    /**
     * Оплачено сертификатом. Деньги пришли раньше, при продаже бланка: в движения денег
     * не идёт, но в сверке оплаты с итогом участвует.
     */
    @Column(name = "certificate_minor", nullable = false)
    private long certificateMinor;

    /**
     * Бланк гасится при проведении и возвращается при распроведении, а не только сторно:
     * иначе отмена чека из админки оставляла бы остаток списанным.
     */
    @Column(name = "certificate_id")
    private UUID certificateId;

    /** Причина списания; NULL — из админки, там её не выбирают. */
    @Enumerated(EnumType.STRING)
    @Column(name = "write_off_reason")
    private WriteOffReason writeOffReason;

    // список документов трогает lines каждого документа — грузим пачками, без N+1
    @OneToMany(mappedBy = "doc", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo ASC")
    @BatchSize(size = 50)
    private List<StockDocLine> lines = new ArrayList<>();

    /** Для {@code DocService.create}: id, номер и статус ставит он; null в moment/warehouseId — «сейчас» и склад по умолчанию. */
    public static StockDoc draft(
            DocType type,
            LocalDateTime moment,
            UUID warehouseId,
            String comment,
            List<StockDocLine> lines
    ) {
        StockDoc doc = new StockDoc();
        doc.setDocType(type);
        doc.setMoment(moment);
        doc.setWarehouseId(warehouseId);
        doc.setComment(comment);
        doc.setLines(new ArrayList<>(lines));
        return doc;
    }

    public boolean isPosted() {
        return status == DocStatus.POSTED;
    }

    public void requireDraft(String action) {
        if (status != DocStatus.DRAFT) {
            throw new ConflictException(action + " можно только черновик, статус: " + status);
        }
    }

    public void requirePosted(String action) {
        if (status != DocStatus.POSTED) {
            throw new ConflictException(action + " можно только проведённый документ, статус: " + status);
        }
    }

    /** Итог и себестоимости к этому моменту уже записало проведение. */
    public void markPosted(LocalDateTime at) {
        requireDraft("Провести");
        status = DocStatus.POSTED;
        postedAt = at;
    }

    public void markUnposted() {
        requirePosted("Распровести");
        status = DocStatus.DRAFT;
        postedAt = null;
    }

    public void cancel() {
        if (status != DocStatus.DRAFT) {
            throw new ConflictException("Отменить можно только черновик; проведённый документ сначала распроведите");
        }
        status = DocStatus.CANCELLED;
    }

    /** У черновика итог ещё не зафиксирован — считаем по строкам. */
    public long currentSumMinor() {
        if (status != DocStatus.DRAFT) {
            return sumMinor;
        }
        return lines.stream()
                .mapToLong(StockDocLine::sumMinor)
                .sum();
    }
}
