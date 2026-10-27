package com.aromatica.modules.masterdata.catalog.productmodel.controller;

import com.aromatica.modules.masterdata.catalog.productmodel.dtos.ProductModelDtos.ImageDto;
import com.aromatica.modules.masterdata.catalog.productmodel.endpoint.ProductModelImageEndpoint;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@Tag(name = "Каталог: фото")
@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ProductModelImageController {

    ProductModelImageEndpoint imageEndpoint;

    @Operation(summary = "Список фото продукта")
    @PreAuthorize("hasAnyRole('ADMIN', 'MERCHANDISER', 'ONLINE_MANAGER')")
    @GetMapping("/api/admin/product-models/{modelId}/images")
    public List<ImageDto> list(@PathVariable UUID modelId) {
        return imageEndpoint.list(modelId);
    }

    @Operation(summary = "Загрузить фото продукта")
    @PreAuthorize("hasAnyRole('ADMIN', 'MERCHANDISER', 'ONLINE_MANAGER')")
    @PostMapping("/api/admin/product-models/{modelId}/images")
    public ImageDto upload(
            @PathVariable UUID modelId,
            @RequestParam("file") MultipartFile file
    ) {
        return imageEndpoint.upload(modelId, file);
    }

    @Operation(summary = "Сделать фото главным")
    @PreAuthorize("hasAnyRole('ADMIN', 'MERCHANDISER', 'ONLINE_MANAGER')")
    @PostMapping("/api/admin/product-models/images/{id}/main")
    public ImageDto makeMain(@PathVariable UUID id) {
        return imageEndpoint.makeMain(id);
    }

    @Operation(summary = "Удалить фото")
    @PreAuthorize("hasAnyRole('ADMIN', 'MERCHANDISER', 'ONLINE_MANAGER')")
    @DeleteMapping("/api/admin/product-models/images/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        imageEndpoint.delete(id);
    }

    // <img> не шлёт заголовок авторизации; фото не секрет, UUID в ссылке непредсказуем (см. SecurityConfig)
    @Operation(
            summary = "Получить фото",
            description = "Открыто без токена: ссылку можно ставить прямо в <img>. Отдаёт байты картинки.",
            security = {}
    )
    @GetMapping("/api/images/{id}")
    public ResponseEntity<byte[]> image(@PathVariable UUID id) {
        return imageEndpoint.image(id);
    }
}
