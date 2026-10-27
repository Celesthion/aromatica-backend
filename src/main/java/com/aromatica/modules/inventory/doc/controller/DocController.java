package com.aromatica.modules.inventory.doc.controller;

import com.aromatica.common.search.PageResponse;
import com.aromatica.modules.inventory.doc.dtos.DocDtos.DocDto;
import com.aromatica.modules.inventory.doc.dtos.DocDtos.DocListItem;
import com.aromatica.modules.inventory.doc.dtos.DocDtos.DocRequest;
import com.aromatica.modules.inventory.doc.dtos.DocDtos.EventDto;
import com.aromatica.modules.inventory.doc.endpoint.DocEndpoint;
import com.aromatica.modules.inventory.doc.search.DocSearchRequest;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "Склад: документы", description = "Складские документы: черновики, проведение, распроведение, отмена. Оплата документа — в разделе «Деньги», только администратор.")
@RestController
@PreAuthorize("hasAnyRole('ADMIN', 'MERCHANDISER', 'ONLINE_MANAGER', 'SENIOR_SELLER')")
@RequestMapping("/api/admin/docs")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DocController {

    DocEndpoint docEndpoint;

    @Operation(summary = "Создать документ")
    @PostMapping
    public DocDto create(@Valid @RequestBody DocRequest request) {
        return docEndpoint.create(request);
    }

    @Operation(summary = "Изменить документ")
    @PutMapping("/{id}")
    public DocDto update(
            @PathVariable UUID id,
            @Valid @RequestBody DocRequest request
    ) {
        return docEndpoint.update(id, request);
    }

    @Operation(summary = "Провести документ")
    @PostMapping("/{id}/post")
    public DocDto post(@PathVariable UUID id) {
        return docEndpoint.post(id);
    }

    @Operation(summary = "Распровести документ")
    @PostMapping("/{id}/unpost")
    public DocDto unpost(@PathVariable UUID id) {
        return docEndpoint.unpost(id);
    }

    @Operation(summary = "Отменить документ")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@PathVariable UUID id) {
        docEndpoint.cancel(id);
    }

    @Operation(summary = "История документа", description = "Кто и когда провёл, распровёл, отменил, оплатил или сторнировал документ.")
    @GetMapping("/{id}/events")
    public List<EventDto> events(@PathVariable UUID id) {
        return docEndpoint.events(id);
    }

    @Operation(summary = "Получить документ")
    @GetMapping("/{id}")
    public DocDto get(@PathVariable UUID id) {
        return docEndpoint.get(id);
    }

    @Operation(summary = "Поиск документов")
    @PostMapping("/search")
    public PageResponse<DocListItem> search(@RequestBody DocSearchRequest request) {
        return docEndpoint.search(request);
    }

}
