package com.aromatica.modules.masterdata.party.dtos;

import com.aromatica.modules.masterdata.party.model.Counterparty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

/** DTO контрагентов: аудит наружу не отдаём. */
public final class CounterpartyDtos {

    private CounterpartyDtos() {}

    public record CounterpartyDto(
            UUID id,
            String name,
            Counterparty.Type type,
            String phone,
            Counterparty.Gender gender,
            LocalDate birthDate,
            String note,
            boolean archived
    ) {}

    public record CounterpartyRequest(

            @NotBlank(message = "Не указано имя контрагента")
            @Size(max = 512)
            String name,

            Counterparty.Type type,

            // обязателен только в API: у старых контрагентов телефона нет, колонка nullable
            @NotBlank(message = "Не указан телефон")
            @Pattern(regexp = "\\+?[0-9]{6,20}", message = "Номер: только цифры, допускается + в начале — без пробелов и других знаков")
            String phone,

            Counterparty.Gender gender,

            LocalDate birthDate,

            @Size(max = 4000)
            String note
    ) {}
}
