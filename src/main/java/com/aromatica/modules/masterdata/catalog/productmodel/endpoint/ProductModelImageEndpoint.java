package com.aromatica.modules.masterdata.catalog.productmodel.endpoint;

import com.aromatica.modules.masterdata.catalog.productmodel.dtos.ProductModelDtos.ImageDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

public interface ProductModelImageEndpoint {

    List<ImageDto> list(UUID modelId);

    ImageDto upload(UUID modelId, MultipartFile file);

    ImageDto makeMain(UUID id);

    void delete(UUID id);

    /** Картинка по ссылке: байты, её тип и долгий кэш (содержимое по id не меняется). */
    ResponseEntity<byte[]> image(UUID id);
}
