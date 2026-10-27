package com.aromatica.modules.inventory.warehouse.service.impl;

import com.aromatica.common.error.ConflictException;
import com.aromatica.modules.inventory.warehouse.event.WarehouseArchivingEvent;
import com.aromatica.modules.inventory.warehouse.model.Warehouse;
import com.aromatica.modules.inventory.warehouse.repository.WarehouseRepository;
import com.aromatica.modules.inventory.warehouse.service.WarehouseService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.aromatica.common.util.Strings.blankToNull;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class WarehouseServiceImpl implements WarehouseService {

    WarehouseRepository repository;
    ApplicationEventPublisher events;

    @Override
    public List<Warehouse> list() {
        return repository.findAll().stream()
                .filter(w -> !w.isArchived())
                .sorted((a, b) -> Boolean.compare(b.isDefault(), a.isDefault()))
                .toList();
    }

    @Transactional
    @Override
    public Warehouse create(Warehouse w) {
        w.setId(UUID.randomUUID());
        w.setName(w.getName().trim());
        w.setAddress(blankToNull(w.getAddress()));
        // без склада по умолчанию остатки, документы и касса падают
        if (repository.findFirstByIsDefaultTrue().isEmpty()) {
            w.setDefault(true);
        }
        Warehouse saved = repository.save(w);
        // у каждого склада магазина своя полка: туда кладутся возвраты и налитое
        Warehouse shelf = new Warehouse();
        shelf.setId(UUID.randomUUID());
        shelf.setName("Полка · " + saved.getName());
        shelf.setKind(Warehouse.Kind.SHELF);
        shelf.setParentId(saved.getId());
        repository.save(shelf);
        return saved;
    }

    @Transactional
    @Override
    public Warehouse update(
            UUID id,
            Warehouse changes
    ) {
        Warehouse w = get(id);
        w.setName(changes.getName().trim());
        w.setAddress(blankToNull(changes.getAddress()));
        return w;
    }

    @Transactional
    @Override
    public Warehouse makeDefault(UUID id) {
        Warehouse target = get(id);
        repository.findAll().forEach(w -> w.setDefault(w.getId().equals(id)));
        return target;
    }

    @Transactional
    @Override
    public Warehouse setArchived(
            UUID id,
            boolean archived
    ) {
        Warehouse w = get(id);
        if (w.isArchived() == archived) {
            return w;
        }
        if (!archived) {
            w.setArchived(false);
            return w;
        }
        if (w.isDefault()) {
            throw new ConflictException("Нельзя архивировать склад по умолчанию — сначала назначьте другой");
        }
        // остатки проверяет модуль остатков, склад о них не знает
        events.publishEvent(new WarehouseArchivingEvent(w));
        w.setArchived(true);
        return w;
    }

    @Override
    public @Nullable String nameOf(@Nullable UUID id) {
        return id == null ? null : repository.findById(id).map(Warehouse::getName).orElse(null);
    }

    @Override
    public Map<UUID, String> names(Collection<UUID> ids) {
        return repository.findAllById(ids.stream().filter(Objects::nonNull).distinct().toList()).stream()
                .collect(Collectors.toMap(Warehouse::getId, Warehouse::getName));
    }

    @Override
    public List<Warehouse> listActive() {
        // смену открывают на складе магазина, полка для кассы не годится
        return repository.findAll().stream().filter(w -> !w.isArchived() && !w.isShelf()).toList();
    }

    private Warehouse get(UUID id) {
        return repository.require(id, "Склад не найден");
    }
}
