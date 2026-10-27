package com.aromatica.modules.pos.draft.controller;

import com.aromatica.modules.pos.draft.dtos.DraftDtos.CreateDraftRequest;
import com.aromatica.modules.pos.draft.dtos.DraftDtos.DraftDto;
import com.aromatica.modules.pos.draft.endpoint.PosDraftEndpoint;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "Касса: отложенные чеки", description = "Черновики корзины кассы.")
@RestController
@PreAuthorize("hasAnyRole('ADMIN', 'SELLER', 'SENIOR_SELLER')")
@RequestMapping("/api/pos")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PosDraftController {

    PosDraftEndpoint draftEndpoint;

    @Operation(summary = "Отложенные чеки смены")
    @GetMapping("/shifts/{shiftId}/drafts")
    public List<DraftDto> list(@PathVariable UUID shiftId) {
        return draftEndpoint.list(shiftId);
    }

    @Operation(summary = "Отложить чек")
    @PostMapping("/shifts/{shiftId}/drafts")
    public DraftDto create(
            @PathVariable UUID shiftId,
            @Valid @RequestBody CreateDraftRequest request
    ) {
        return draftEndpoint.create(shiftId, request);
    }

    @Operation(summary = "Удалить отложенный чек")
    @DeleteMapping("/drafts/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        draftEndpoint.delete(id);
    }
}
