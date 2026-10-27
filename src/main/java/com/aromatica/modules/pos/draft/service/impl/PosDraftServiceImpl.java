package com.aromatica.modules.pos.draft.service.impl;

import com.aromatica.common.error.BusinessRuleException;
import com.aromatica.modules.pos.draft.model.PosDraft;
import com.aromatica.modules.pos.draft.repository.PosDraftRepository;
import com.aromatica.modules.pos.draft.service.PosDraftService;
import com.aromatica.modules.pos.shift.model.PosShift;
import com.aromatica.modules.pos.shift.service.PosShiftService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** Отложенные чеки: снапшот корзины в jsonb плюс пара полей для списка, чтобы не разбирать payload. */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PosDraftServiceImpl implements PosDraftService {

    /** Потолок на смену, чтобы отложенные не превращались в свалку. */
    static final int MAX_DRAFTS_PER_SHIFT = 10;

    PosDraftRepository draftRepository;
    PosShiftService shiftService;

    @Override
    public List<PosDraft> list(
            UUID shiftId,
            String login
    ) {
        shiftService.requireAccessibleOpenShift(shiftId, login);
        return draftRepository.findByShiftIdOrderByCreatedAtDesc(shiftId);
    }

    @Transactional
    @Override
    public PosDraft create(
            UUID shiftId,
            PosDraft draft,
            String login
    ) {
        PosShift shift = shiftService.requireAccessibleOpenShift(shiftId, login);
        // JSON-литерал null приходит строкой "null"
        if (draft.getPayload() == null || "null".equals(draft.getPayload())) {
            throw new BusinessRuleException("Пустой черновик отложить нельзя");
        }
        if (draftRepository.countByShiftId(shift.getId()) >= MAX_DRAFTS_PER_SHIFT) {
            throw new BusinessRuleException(
                    "Отложенных чеков уже " + MAX_DRAFTS_PER_SHIFT + " — освободите место");
        }
        draft.setId(UUID.randomUUID());
        draft.setShiftId(shift.getId());
        return draftRepository.save(draft);
    }

    @Transactional
    @Override
    public void delete(
            UUID id,
            String login
    ) {
        PosDraft draft = draftRepository.require(id, "Черновик не найден");
        // доступ проверяем через смену: чужой склад или закрытая смена не пройдут
        shiftService.requireAccessibleOpenShift(draft.getShiftId(), login);
        draftRepository.delete(draft);
    }
}
