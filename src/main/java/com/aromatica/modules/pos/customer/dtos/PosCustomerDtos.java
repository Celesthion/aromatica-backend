package com.aromatica.modules.pos.customer.dtos;

import com.aromatica.modules.masterdata.catalog.product.model.CatalogProduct;
import com.aromatica.modules.masterdata.party.model.Counterparty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Покупатели для кассы: в отличие от админского CounterpartyDto — только поля,
 * по которым узнают человека у прилавка.
 */
public final class PosCustomerDtos {

    private PosCustomerDtos() {}

    public record PosCustomerDto(
            UUID id,
            String name,
            String phone,
            Counterparty.Gender gender,
            LocalDate birthDate
    ) {}

    public record PosCustomerRequest(

            @NotBlank(message = "Не указано имя покупателя")
            @Size(max = 512)
            String name,

            @NotBlank(message = "Не указан телефон")
            @Pattern(regexp = "\\+?[0-9]{6,20}",
                    message = "Номер: только цифры, допускается + в начале — без пробелов и других знаков")
            String phone,

            Counterparty.Gender gender,

            LocalDate birthDate
    ) {}

    public record PurchaseDto(
            UUID docId,
            long number,
            LocalDateTime moment,
            long sumMinor,
            int positions,
            List<PurchaseLineDto> lines
    ) {}

    /** Себестоимость наружу не отдаём. */
    public record PurchaseLineDto(
            String productName,
            CatalogProduct.Unit unit,
            BigDecimal qty,
            long sumMinor,
            boolean gift
    ) {}
}
