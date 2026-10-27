package com.aromatica.modules.pos.sale.usecase;

import com.aromatica.common.error.ConflictException;
import com.aromatica.modules.inventory.doc.model.DocEvent;
import com.aromatica.modules.inventory.doc.model.DocType;
import com.aromatica.modules.inventory.doc.model.StockDoc;
import com.aromatica.modules.inventory.doc.service.DocEventService;
import com.aromatica.modules.inventory.doc.service.DocService;
import com.aromatica.modules.inventory.doc.service.PostingService;
import com.aromatica.modules.pos.reservation.service.ReservationService;
import com.aromatica.modules.pos.shift.model.PosShift;
import com.aromatica.modules.pos.shift.service.PosShiftService;
import com.aromatica.security.auth.service.UserDirectory;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class VoidReceiptUseCase {

    PosShiftService shiftService;
    DocService docService;
    PostingService postingService;
    DocEventService docEventService;
    ReservationService reservationService;
    UserDirectory users;

    /**
     * Сторно чека своей открытой смены: распроведение и отмена одной операцией.
     * Только свой чек — чужой сторнирует админ.
     */
    @Transactional
    public void handle(
            UUID shiftId,
            UUID docId,
            String login
    ) {
        PosShift shift = shiftService.lockOpenShift(shiftId, login);
        // тоже под блокировкой: второе сторно должно увидеть первое
        StockDoc doc = docService.findForUpdate(docId);
        if (!shift.getId().equals(doc.getShiftId())) {
            throw new ConflictException("Чек принадлежит другой смене");
        }
        UUID currentUserId = users.idByLogin(login);
        if (doc.getCreatedBy() != null && !doc.getCreatedBy().equals(currentUserId)) {
            throw new AccessDeniedException("Сторнировать можно только свой чек");
        }
        if (doc.getDocType() != DocType.SALE && doc.getDocType() != DocType.RETURN) {
            throw new ConflictException("Сторнировать можно только чек продажи или возврата");
        }
        if (!doc.isPosted()) {
            throw new ConflictException("Чек уже сторнирован");
        }
        // после возврата товар и деньги вернулись бы дважды — сначала сторнируется возврат
        if (doc.getDocType() == DocType.SALE
                && !docService.findPostedBySourceDoc(docId).isEmpty()) {
            throw new ConflictException(
                    "По чеку уже оформлен возврат — сторнируйте сначала его");
        }
        // сертификат вернёт само распроведение (PostingService.unpost)
        postingService.unpost(docId);
        docService.cancel(docId);
        // бронь, выкупленную этим чеком, возвращаем клиенту, как и товар
        reservationService.restoreAfterVoid(docId);
        docEventService.record(docId, DocEvent.Action.VOID, login);
    }
}
