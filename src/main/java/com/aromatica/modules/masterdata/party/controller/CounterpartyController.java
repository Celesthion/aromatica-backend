package com.aromatica.modules.masterdata.party.controller;

import com.aromatica.common.search.PageResponse;
import com.aromatica.modules.masterdata.party.dtos.CounterpartyDtos.CounterpartyDto;
import com.aromatica.modules.masterdata.party.dtos.CounterpartyDtos.CounterpartyRequest;
import com.aromatica.modules.masterdata.party.endpoint.CounterpartyEndpoint;
import com.aromatica.modules.masterdata.party.search.CounterpartySearchRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Контрагенты")
@RestController
@PreAuthorize("hasAnyRole('ADMIN', 'MERCHANDISER')")
@RequestMapping("/api/admin/counterparties")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CounterpartyController {

    CounterpartyEndpoint counterpartyEndpoint;

    @Operation(summary = "Поиск контрагентов")
    @PostMapping("/search")
    public PageResponse<CounterpartyDto> search(@RequestBody CounterpartySearchRequest request) {
        return counterpartyEndpoint.search(request);
    }

    @Operation(summary = "Создать контрагента")
    @PostMapping
    public CounterpartyDto create(@Valid @RequestBody CounterpartyRequest request) {
        return counterpartyEndpoint.create(request);
    }

    @Operation(summary = "Обновить контрагента")
    @PutMapping("/{id}")
    public CounterpartyDto update(
            @PathVariable UUID id,
            @Valid @RequestBody CounterpartyRequest request
    ) {
        return counterpartyEndpoint.update(id, request);
    }

    @Operation(
            summary = "Отправить контрагента в архив",
            description = "Не удаляет, только архивирует: на контрагента могут ссылаться документы"
    )
    @PostMapping("/{id}/archive")
    public CounterpartyDto archive(@PathVariable UUID id) {
        return counterpartyEndpoint.setArchived(id, true);
    }

    @Operation(summary = "Вернуть контрагента из архива")
    @PostMapping("/{id}/restore")
    public CounterpartyDto restore(@PathVariable UUID id) {
        return counterpartyEndpoint.setArchived(id, false);
    }
}
