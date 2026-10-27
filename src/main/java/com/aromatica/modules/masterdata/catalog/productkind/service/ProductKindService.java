package com.aromatica.modules.masterdata.catalog.productkind.service;

import com.aromatica.modules.masterdata.catalog.productkind.model.ProductKind;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Справочник форм. Системные менять нельзя; пользовательские не удаляются, а уходят в архив. */
public interface ProductKindService {

    List<ProductKind> list(boolean includeArchived);

    Optional<ProductKind> get(String code);

    ProductKind find(String code);

    List<ProductKind> findAllById(Collection<String> codes);

    /** Новая форма: код выдаёт система, имя уникально без учёта регистра. */
    ProductKind create(ProductKind kind);

    /**
     * Единица после создания не меняется, имя — только у несистемных форм.
     * Флаг «в подарках» и вид можно менять у любых: поведению это не мешает.
     */
    ProductKind update(String code, ProductKind changes);

    ProductKind setArchived(String code, boolean archived);

    /** Форма для товара: должна существовать и не быть в архиве, иначе 400. */
    ProductKind requireActive(String code);
}
