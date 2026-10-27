package com.aromatica.modules.masterdata.catalog.brand.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public final class BrandDtos {

    private BrandDtos() {}

    public record BrandDto(
            UUID id,
            String name,
            boolean archived
    ) {}

    public record BrandRequest(

            @NotBlank(message = "Не указано название бренда")
            @Size(max = 255)
            String name
    ) {}
}
