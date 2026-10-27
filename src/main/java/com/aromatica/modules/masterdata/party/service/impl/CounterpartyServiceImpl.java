package com.aromatica.modules.masterdata.party.service.impl;

import com.aromatica.common.error.ConflictException;
import com.aromatica.modules.masterdata.party.model.Counterparty;
import com.aromatica.modules.masterdata.party.repository.CounterpartyRepository;
import com.aromatica.modules.masterdata.party.service.CounterpartyService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CounterpartyServiceImpl implements CounterpartyService {

    CounterpartyRepository repository;

    @Transactional
    @Override
    public Counterparty create(Counterparty counterparty) {
        normalize(counterparty);
        requirePhoneFree(counterparty.getPhone(), null);
        counterparty.setId(UUID.randomUUID());
        if (counterparty.getType() == null) {
            counterparty.setType(Counterparty.Type.SUPPLIER);
        }
        return repository.save(counterparty);
    }

    @Transactional
    @Override
    public Counterparty update(
            UUID id,
            Counterparty changes
    ) {
        normalize(changes);
        requirePhoneFree(changes.getPhone(), id);
        Counterparty c = find(id);
        c.setName(changes.getName());
        if (changes.getType() != null) {
            c.setType(changes.getType());
        }
        c.setPhone(changes.getPhone());
        c.setGender(changes.getGender());
        c.setBirthDate(changes.getBirthDate());
        c.setNote(changes.getNote());
        return c;
    }

    @Transactional
    @Override
    public Counterparty setArchived(
            UUID id,
            boolean archived
    ) {
        Counterparty c = find(id);
        c.setArchived(archived);
        return c;
    }

    private static void normalize(Counterparty c) {
        c.setName(c.getName().trim());
        c.setPhone(c.getPhone() != null ? c.getPhone().trim() : null);
    }

    /** Проверка до вставки: по ошибке уникального индекса человек ничего не поймёт. exceptId — своя запись при правке. */
    private void requirePhoneFree(
            String phone,
            UUID exceptId
    ) {
        if (phone == null || phone.isBlank()) {
            return;
        }
        boolean taken = exceptId == null
                ? repository.existsByPhone(phone)
                : repository.existsByPhoneAndIdNot(phone, exceptId);
        if (taken) {
            throw new ConflictException("Контрагент с номером " + phone + " уже есть");
        }
    }

    @Override
    public Counterparty find(UUID id) {
        return repository.require(id, "Контрагент не найден");
    }

    @Override
    public @Nullable String nameOf(@Nullable UUID id) {
        return id == null ? null : repository.findById(id).map(Counterparty::getName).orElse(null);
    }

    @Override
    public Map<UUID, String> names(Collection<UUID> ids) {
        Map<UUID, String> names = new HashMap<>();
        repository.findAllById(ids.stream().filter(Objects::nonNull).distinct().toList())
                .forEach(c -> names.put(c.getId(), c.getName()));
        return names;
    }

    @Override
    public Map<UUID, Counterparty> byIds(Collection<UUID> ids) {
        Map<UUID, Counterparty> found = new HashMap<>();
        repository.findAllById(ids.stream().filter(Objects::nonNull).distinct().toList())
                .forEach(c -> found.put(c.getId(), c));
        return found;
    }

}
