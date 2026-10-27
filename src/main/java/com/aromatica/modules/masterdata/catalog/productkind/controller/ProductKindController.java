package com.aromatica.modules.masterdata.catalog.productkind.controller;

import com.aromatica.modules.masterdata.catalog.productkind.dtos.ProductKindDtos.KindDto;
import com.aromatica.modules.masterdata.catalog.productkind.dtos.ProductKindDtos.KindRequest;
import com.aromatica.modules.masterdata.catalog.productkind.endpoint.ProductKindEndpoint;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Каталог: формы товара")
@RestController
@PreAuthorize("hasAnyRole('ADMIN', 'MERCHANDISER', 'ONLINE_MANAGER')")
@RequestMapping("/api/admin/product-kinds")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ProductKindController {

    ProductKindEndpoint kindEndpoint;

    @Operation(summary = "Список форм товара")
    @GetMapping
    public List<KindDto> list(@RequestParam(defaultValue = "false") boolean includeArchived) {
        return kindEndpoint.list(includeArchived);
    }

    @Operation(summary = "Создать форму товара")
    @PostMapping
    public KindDto create(@Valid @RequestBody KindRequest request) {
        return kindEndpoint.create(request);
    }

    @Operation(
            summary = "Обновить форму товара",
            description = "Имя меняется только у несистемных форм, флаг «в подарках» — у любых"
    )
    @PutMapping("/{code}")
    public KindDto update(
            @PathVariable String code,
            @Valid @RequestBody KindRequest request
    ) {
        return kindEndpoint.update(code, request);
    }

    @Operation(summary = "Отправить форму товара в архив", description = "Системные формы не архивируются")
    @PostMapping("/{code}/archive")
    public KindDto archive(@PathVariable String code) {
        return kindEndpoint.setArchived(code, true);
    }

    @Operation(summary = "Вернуть форму товара из архива")
    @PostMapping("/{code}/restore")
    public KindDto restore(@PathVariable String code) {
        return kindEndpoint.setArchived(code, false);
    }
}
