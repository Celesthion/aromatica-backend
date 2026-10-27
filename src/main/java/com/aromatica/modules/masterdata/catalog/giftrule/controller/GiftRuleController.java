package com.aromatica.modules.masterdata.catalog.giftrule.controller;

import com.aromatica.modules.masterdata.catalog.giftrule.dtos.GiftRuleDtos.RuleDto;
import com.aromatica.modules.masterdata.catalog.giftrule.dtos.GiftRuleDtos.RuleRequest;
import com.aromatica.modules.masterdata.catalog.giftrule.endpoint.GiftRuleEndpoint;
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

@Tag(name = "Акции и подарки", description = "Акции с подарками. Доступ только у ADMIN")
@RestController
@PreAuthorize("hasRole('ADMIN')")
@RequestMapping("/api/admin/gift-rules")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class GiftRuleController {

    GiftRuleEndpoint giftRuleEndpoint;

    @Operation(summary = "Список акций")
    @GetMapping
    public List<RuleDto> list() {
        return giftRuleEndpoint.list();
    }

    @Operation(summary = "Создать акцию")
    @PostMapping
    public RuleDto create(@Valid @RequestBody RuleRequest request) {
        return giftRuleEndpoint.create(request);
    }

    @Operation(summary = "Обновить акцию")
    @PutMapping("/{id}")
    public RuleDto update(
            @PathVariable UUID id,
            @Valid @RequestBody RuleRequest request
    ) {
        return giftRuleEndpoint.update(id, request);
    }

    @Operation(summary = "Включить или выключить акцию", description = "Переключает активность, акция не удаляется")
    @PostMapping("/{id}/toggle")
    public RuleDto toggle(@PathVariable UUID id) {
        return giftRuleEndpoint.toggleActive(id);
    }

    @Operation(summary = "Удалить акцию")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        giftRuleEndpoint.delete(id);
    }
}
