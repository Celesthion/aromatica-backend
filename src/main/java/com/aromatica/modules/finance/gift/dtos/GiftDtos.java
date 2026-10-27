package com.aromatica.modules.finance.gift.dtos;

import com.aromatica.modules.finance.gift.model.GiftCertificate;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.LocalDateTime;
import java.util.UUID;

/** Сертификаты наружу: без аудита и внутренних полей. */
public final class GiftDtos {

    private GiftDtos() {}

    public record GiftCertificateDto(

            UUID id,

            String code,

            long nominalMinor,

            long balanceMinor,

            GiftCertificate.Status status,

            String comment,

            LocalDateTime soldAt,

            String soldBy,

            /** Покупатель, а не предъявитель. */
            UUID buyerId,

            String buyerName,

            String buyerPhone
    ) {}

    public record GenerateBatchRequest(

            @Min(value = 1, message = "Сколько бланков — минимум 1")
            @Max(value = 1000, message = "За раз — не больше 1000 бланков")
            int count,

            @Positive(message = "Номинал должен быть больше нуля")
            long nominalMinor,

            String comment
    ) {}

    public record SellRequest(

            @NotBlank(message = "Не указан код бланка")
            String code,

            @PositiveOrZero
            long cashMinor,

            @PositiveOrZero
            long noCashMinor,

            @NotNull(message = "Не указан покупатель сертификата")
            UUID customerId
    ) {}

    public record BlockRequest(
            String comment
    ) {}

    /** Обязательства — непогашенные остатки проданных бланков: столько магазин должен товаром. */
    public record GiftStats(
            long liabilityMinor,
            long inStock,
            long total
    ) {}

    /** Знаковая сумма: погашение минусом, возврат плюсом. */
    public record UseDto(
            UUID docId,
            long amountMinor,
            LocalDateTime at
    ) {}

    /** Проверка кода на кассе: без аудита и продавца, кассиру они не нужны. */
    public record CertificateCheck(
            boolean payable,
            String reason,
            UUID id,
            String code,
            long nominalMinor,
            long balanceMinor,
            GiftCertificate.Status status
    ) {}
}
