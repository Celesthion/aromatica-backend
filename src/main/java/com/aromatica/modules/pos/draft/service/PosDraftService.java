package com.aromatica.modules.pos.draft.service;

import com.aromatica.modules.pos.draft.model.PosDraft;

import java.util.List;
import java.util.UUID;

/**
 * Отложенные чеки кассы. Удаляются вместе со сменой (ON DELETE CASCADE),
 * доступ проверяется через смену, как у чеков.
 */
public interface PosDraftService {

    /** Новые сверху. */
    List<PosDraft> list(UUID shiftId, String login);

    /** Не больше 10 черновиков на смену, иначе 400. */
    PosDraft create(UUID shiftId, PosDraft draft, String login);

    /** Черновик забрали в работу или выкинули. */
    void delete(UUID id, String login);
}
