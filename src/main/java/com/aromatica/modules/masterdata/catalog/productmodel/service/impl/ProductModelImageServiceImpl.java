package com.aromatica.modules.masterdata.catalog.productmodel.service.impl;

import com.aromatica.common.error.BusinessRuleException;
import com.aromatica.common.error.NotFoundException;
import com.aromatica.modules.masterdata.catalog.productmodel.model.MainImageRow;
import com.aromatica.modules.masterdata.catalog.productmodel.model.ProductModelImage;
import com.aromatica.modules.masterdata.catalog.productmodel.model.ProductModelImageSummary;
import com.aromatica.modules.masterdata.catalog.productmodel.repository.ProductModelImageRepository;
import com.aromatica.modules.masterdata.catalog.productmodel.service.ProductModelImageService;
import jakarta.persistence.EntityManager;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ProductModelImageServiceImpl implements ProductModelImageService {

    /** Потолок на файл: это фото товара, а не архив. Клиент ужимает до ~1200 px. */
    static final int MAX_BYTES = 3 * 1024 * 1024;

    ProductModelImageRepository repository;
    EntityManager entityManager;

    @Override
    public List<ProductModelImageSummary> listOf(UUID modelId) {
        return repository.findSummariesByModelIdOrderBySortAscCreatedAtAsc(modelId);
    }

    @Override
    public ProductModelImage find(UUID id) {
        return repository.require(id, "Фотография не найдена");
    }

    @Override
    public Map<UUID, UUID> mainImageIds(Collection<UUID> modelIds) {
        if (modelIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, UUID> out = new HashMap<>();
        for (MainImageRow row : repository.findMainIdsByModelIds(modelIds)) {
            out.put(row.getModelId(), row.getImageId());
        }
        return out;
    }

    @Transactional
    @Override
    public ProductModelImage add(
            UUID modelId,
            String contentType,
            byte[] bytes
    ) {
        if (bytes == null || bytes.length == 0) {
            throw new BusinessRuleException("Пустой файл");
        }
        if (bytes.length > MAX_BYTES) {
            throw new BusinessRuleException("Файл больше 3 МБ — сожмите фотографию");
        }
        // тип определяем по содержимому, а не по имени файла и не со слов клиента:
        // браузер не покажет картинку, отданную с чужим Content-Type
        String type = sniffType(bytes);
        if (type == null) {
            throw new BusinessRuleException("Поддерживаются JPEG, PNG, WebP и AVIF");
        }
        ProductModelImage image = new ProductModelImage();
        image.setId(UUID.randomUUID());
        image.setModelId(modelId);
        image.setContentType(type);
        image.setBytes(bytes);
        image.setSizeBytes(bytes.length);
        image.setSort(repository.maxSort(modelId) + 1);
        // первая — сразу основная, иначе карточка без картинки, пока не нажмут «сделать основной»
        image.setMain(repository.countByModelId(modelId) == 0);
        // persist, а не save: у фото свой id и нет версии, save счёл бы его существующим
        // и перед вставкой перечитал бы строку вместе с байтами
        entityManager.persist(image);
        return image;
    }

    /** Формат по сигнатуре: с сайтов часто приходит AVIF, названный .png. Неизвестный — null. */
    static String sniffType(byte[] b) {
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') {
            return "image/png";
        }
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (b.length >= 12 && matches(b, 0, "RIFF") && matches(b, 8, "WEBP")) {
            return "image/webp";
        }
        // ISO-BMFF: ftyp-бокс со смещения 4, за ним бренд avif или avis
        if (b.length >= 12 && matches(b, 4, "ftyp") && (matches(b, 8, "avif") || matches(b, 8, "avis"))) {
            return "image/avif";
        }
        return null;
    }

    private static boolean matches(
            byte[] b,
            int off,
            String ascii
    ) {
        for (int i = 0; i < ascii.length(); i++) {
            if (b[off + i] != ascii.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    @Transactional
    @Override
    public ProductModelImageSummary makeMain(UUID id) {
        ProductModelImageSummary target = summary(id);
        // флаг со старой основной снимаем до простановки новой: уникальный индекс
        // не терпит двух основных даже внутри одной транзакции
        repository.clearMain(target.getModelId());
        repository.setMain(id);
        return summary(id);
    }

    @Transactional
    @Override
    public void delete(UUID id) {
        ProductModelImageSummary image = summary(id);
        repository.deleteWithoutLoading(id);
        if (image.isMain()) {
            // у продукта с фотографиями всегда должна быть основная
            repository.findIdsByModelId(image.getModelId()).stream()
                    .findFirst()
                    .ifPresent(repository::setMain);
        }
    }

    private ProductModelImageSummary summary(UUID id) {
        return repository.findSummaryById(id)
                .orElseThrow(() -> new NotFoundException("Фотография не найдена: " + id));
    }
}
