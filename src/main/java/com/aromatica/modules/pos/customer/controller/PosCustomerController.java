package com.aromatica.modules.pos.customer.controller;

import com.aromatica.modules.pos.customer.dtos.PosCustomerDtos.PosCustomerDto;
import com.aromatica.modules.pos.customer.dtos.PosCustomerDtos.PosCustomerRequest;
import com.aromatica.modules.pos.customer.dtos.PosCustomerDtos.PurchaseDto;
import com.aromatica.modules.pos.customer.endpoint.PosCustomerEndpoint;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "Касса: покупатели", description = "Отдельно от /api/admin/counterparties: у кассы свои роли, весь справочник и поставщики ей недоступны.")
@RestController
@PreAuthorize("hasAnyRole('ADMIN', 'SELLER', 'SENIOR_SELLER')")
@RequestMapping("/api/pos/customers")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PosCustomerController {

    PosCustomerEndpoint customerEndpoint;

    @Operation(summary = "Поиск покупателя", description = "По имени или номеру. На короткий запрос ответ пустой.")
    @GetMapping("/search")
    public List<PosCustomerDto> search(@RequestParam(name = "q", required = false) String q) {
        return customerEndpoint.search(q);
    }

    @Operation(summary = "Добавить покупателя")
    @PostMapping
    public PosCustomerDto create(@Valid @RequestBody PosCustomerRequest request) {
        return customerEndpoint.create(request);
    }

    @Operation(summary = "Покупки покупателя")
    @GetMapping("/{id}/purchases")
    public List<PurchaseDto> purchases(@PathVariable UUID id) {
        return customerEndpoint.purchases(id);
    }
}
