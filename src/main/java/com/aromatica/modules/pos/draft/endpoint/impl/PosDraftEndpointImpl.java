package com.aromatica.modules.pos.draft.endpoint.impl;

import com.aromatica.modules.pos.draft.dtos.DraftDtos.CreateDraftRequest;
import com.aromatica.modules.pos.draft.dtos.DraftDtos.DraftDto;
import com.aromatica.modules.pos.draft.endpoint.PosDraftEndpoint;
import com.aromatica.modules.pos.draft.mapper.DraftMapper;
import com.aromatica.modules.pos.draft.service.PosDraftService;
import com.aromatica.security.auth.authentication.CurrentUser;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PosDraftEndpointImpl implements PosDraftEndpoint {

    PosDraftService draftService;
    DraftMapper mapper;

    @Override
    @Transactional(readOnly = true)
    public List<DraftDto> list(UUID shiftId) {
        return draftService.list(shiftId, CurrentUser.login()).stream().map(mapper::toDto).toList();
    }

    @Override
    @Transactional
    public DraftDto create(
            UUID shiftId,
            CreateDraftRequest request
    ) {
        return mapper.toDto(draftService.create(shiftId, mapper.toEntity(request), CurrentUser.login()));
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        draftService.delete(id, CurrentUser.login());
    }
}
