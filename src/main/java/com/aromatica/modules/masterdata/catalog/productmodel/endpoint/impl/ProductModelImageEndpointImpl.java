package com.aromatica.modules.masterdata.catalog.productmodel.endpoint.impl;

import com.aromatica.common.error.BusinessRuleException;
import com.aromatica.modules.masterdata.catalog.productmodel.dtos.ProductModelDtos.ImageDto;
import com.aromatica.modules.masterdata.catalog.productmodel.endpoint.ProductModelImageEndpoint;
import com.aromatica.modules.masterdata.catalog.productmodel.mapper.ProductModelImageMapper;
import com.aromatica.modules.masterdata.catalog.productmodel.model.ProductModelImage;
import com.aromatica.modules.masterdata.catalog.productmodel.service.ProductModelImageService;
import com.aromatica.modules.masterdata.catalog.productmodel.service.ProductModelService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ProductModelImageEndpointImpl implements ProductModelImageEndpoint {

    ProductModelImageService imageService;
    ProductModelService modelService;
    ProductModelImageMapper mapper;

    @Transactional(readOnly = true)
    @Override
    public List<ImageDto> list(UUID modelId) {
        return imageService.listOf(modelId).stream()
                .map(mapper::toDto)
                .toList();
    }

    @Transactional
    @Override
    public ImageDto upload(
            UUID modelId,
            MultipartFile file
    ) {
        // продукт должен существовать, иначе фотография ни к чему не привязана
        modelService.find(modelId);
        try {
            return mapper.toDto(imageService.add(modelId, file.getContentType(), file.getBytes()));
        } catch (IOException e) {
            throw new BusinessRuleException("Не удалось прочитать файл: " + e.getMessage());
        }
    }

    @Transactional
    @Override
    public ImageDto makeMain(UUID id) {
        return mapper.toDto(imageService.makeMain(id));
    }

    @Transactional
    @Override
    public void delete(UUID id) {
        imageService.delete(id);
    }

    @Transactional(readOnly = true)
    @Override
    public ResponseEntity<byte[]> image(UUID id) {
        ProductModelImage image = imageService.find(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.getContentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic().immutable())
                .body(image.getBytes());
    }

}
