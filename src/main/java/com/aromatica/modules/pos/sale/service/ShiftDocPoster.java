package com.aromatica.modules.pos.sale.service;

import com.aromatica.modules.inventory.doc.model.DocEvent;
import com.aromatica.modules.inventory.doc.model.StockDoc;
import com.aromatica.modules.inventory.doc.service.DocEventService;
import com.aromatica.modules.inventory.doc.service.DocService;
import com.aromatica.modules.inventory.doc.service.PostingService;
import com.aromatica.modules.pos.shift.model.PosShift;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Общие шаги чека, возврата и списания разлива: документ смены, проведение, событие.
 */
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ShiftDocPoster {

    DocService docService;
    PostingService postingService;
    DocEventService docEventService;

    /** Сертификат, исходный чек и причину списания вызывающий ставит на черновик сам. */
    @Transactional
    public StockDoc post(
            StockDoc draft,
            PosShift shift,
            String login
    ) {
        draft.setShiftId(shift.getId());
        StockDoc doc = docService.create(draft);
        StockDoc posted = postingService.post(doc.getId());
        docEventService.record(doc.getId(), DocEvent.Action.POST, login);
        return posted;
    }
}
