package com.aromatica.modules.pos.draft.endpoint;

import com.aromatica.modules.pos.draft.dtos.DraftDtos.CreateDraftRequest;
import com.aromatica.modules.pos.draft.dtos.DraftDtos.DraftDto;

import java.util.List;
import java.util.UUID;

public interface PosDraftEndpoint {

    List<DraftDto> list(UUID shiftId);

    DraftDto create(UUID shiftId, CreateDraftRequest request);

    void delete(UUID id);
}
