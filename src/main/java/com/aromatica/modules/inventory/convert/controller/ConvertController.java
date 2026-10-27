package com.aromatica.modules.inventory.convert.controller;

import com.aromatica.modules.inventory.convert.dtos.ConvertDtos.ConvertRequest;
import com.aromatica.modules.inventory.convert.dtos.ConvertDtos.SplitInfo;
import com.aromatica.modules.inventory.convert.endpoint.ConvertEndpoint;
import com.aromatica.modules.inventory.doc.dtos.DocDtos.DocDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Склад: вскрытие флаконов", description = "Вскрытие флакона или тестера в распив. Доступно администратору и мерчендайзеру.")
@RestController
@PreAuthorize("hasAnyRole('ADMIN', 'MERCHANDISER')")
@RequestMapping("/api/admin/convert")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ConvertController {

    ConvertEndpoint convertEndpoint;

    @Operation(summary = "Вскрыть флакон в распив")
    @PostMapping
    public DocDto open(@Valid @RequestBody ConvertRequest request) {
        return convertEndpoint.open(request);
    }

    @Operation(summary = "Распив аромата", description = "Есть ли уже распив-SKU у аромата этого товара; нужно форме вскрытия.")
    @GetMapping("/split")
    public SplitInfo split(@RequestParam UUID sourceProductId) {
        return convertEndpoint.splitInfo(sourceProductId);
    }
}
