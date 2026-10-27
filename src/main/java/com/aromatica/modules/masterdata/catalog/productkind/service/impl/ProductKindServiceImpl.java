package com.aromatica.modules.masterdata.catalog.productkind.service.impl;

import com.aromatica.common.error.BusinessRuleException;
import com.aromatica.common.error.ConflictException;
import com.aromatica.modules.masterdata.catalog.product.model.CatalogProduct;
import com.aromatica.modules.masterdata.catalog.productkind.model.ProductKind;
import com.aromatica.modules.masterdata.catalog.productkind.repository.ProductKindRepository;
import com.aromatica.modules.masterdata.catalog.productkind.service.ProductKindService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static com.aromatica.common.util.Strings.blankToNull;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ProductKindServiceImpl implements ProductKindService {

    static SecureRandom RANDOM = new SecureRandom();

    ProductKindRepository repository;

    @Override
    public List<ProductKind> list(boolean includeArchived) {
        return repository.findAllByOrderBySystemDescCreatedAtAsc().stream()
                .filter(k -> includeArchived || !k.isArchived())
                .toList();
    }

    @Override
    public Optional<ProductKind> get(String code) {
        return repository.findById(code);
    }

    @Override
    public ProductKind find(String code) {
        return repository.require(code, "Форма не найдена");
    }

    @Override
    public List<ProductKind> findAllById(Collection<String> codes) {
        return repository.findAllById(codes);
    }

    @Transactional
    @Override
    public ProductKind create(ProductKind kind) {
        String trimmed = kind.getName().trim();
        repository.findByNameIgnoreCase(trimmed).ifPresent(k -> {
            throw new BusinessRuleException("Форма уже есть: " + k.getName());
        });
        kind.setCode(generateCode());
        kind.setName(trimmed);
        if (kind.getUnit() == null) {
            kind.setUnit(CatalogProduct.Unit.PCS);
        }
        kind.setIcon(blankToNull(kind.getIcon()));
        kind.setColor(blankToNull(kind.getColor()));
        return repository.save(kind);
    }

    @Transactional
    @Override
    public ProductKind update(
            String code,
            ProductKind changes
    ) {
        ProductKind kind = find(code);
        String trimmed = changes.getName().trim();
        if (!kind.getName().equals(trimmed)) {
            if (kind.isSystem()) {
                throw new ConflictException(
                        "Системная форма «" + kind.getName() + "» не переименовывается");
            }
            repository.findByNameIgnoreCase(trimmed).ifPresent(k -> {
                if (!k.getCode().equals(code)) {
                    throw new BusinessRuleException("Форма уже есть: " + k.getName());
                }
            });
            kind.setName(trimmed);
        }
        kind.setGiftEligible(changes.isGiftEligible());
        kind.setIcon(blankToNull(changes.getIcon()));
        kind.setColor(blankToNull(changes.getColor()));
        return kind;
    }

    @Transactional
    @Override
    public ProductKind setArchived(
            String code,
            boolean archived
    ) {
        ProductKind kind = find(code);
        if (kind.isArchived() == archived) {
            return kind;
        }
        if (kind.isSystem()) {
            throw new ConflictException("Системная форма «" + kind.getName() + "» не редактируется");
        }
        kind.setArchived(archived);
        return kind;
    }

    @Override
    public ProductKind requireActive(String code) {
        ProductKind kind = repository.findById(code)
                .orElseThrow(() -> new BusinessRuleException("Неизвестная форма: " + code));
        if (kind.isArchived()) {
            throw new BusinessRuleException("Форма «" + kind.getName() + "» в архиве");
        }
        return kind;
    }

    /** Код технический и стабильный, на него ссылаются товары. Имя — для людей. */
    private String generateCode() {
        for (int i = 0; i < 20; i++) {
            String code = "C" + Long.toHexString(RANDOM.nextLong() & 0xFFFFFFFFFFFL).toUpperCase();
            if (repository.findById(code).isEmpty()) {
                return code;
            }
        }
        throw new ConflictException("Не удалось сгенерировать код формы");
    }
}
