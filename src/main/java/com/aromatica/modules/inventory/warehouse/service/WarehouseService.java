package com.aromatica.modules.inventory.warehouse.service;

import com.aromatica.modules.inventory.warehouse.model.Warehouse;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Справочник складов: склад по умолчанию всегда один, архивация с ограничениями. */
public interface WarehouseService {

    /** Неархивные склады, склад по умолчанию первым. */
    List<Warehouse> list();

    /** Новый склад магазина вместе с его полкой; первый склад становится складом по умолчанию. */
    Warehouse create(Warehouse warehouse);

    /** Переносит только название и адрес. */
    Warehouse update(UUID id, Warehouse changes);

    Warehouse makeDefault(UUID id);

    /** Движения и история сохраняются. */
    Warehouse setArchived(UUID id, boolean archived);

    @Nullable String nameOf(@Nullable UUID id);

    /** null в списке пропускается. */
    Map<UUID, String> names(Collection<UUID> ids);

    /** Для выбора склада на кассе. */
    List<Warehouse> listActive();
}
