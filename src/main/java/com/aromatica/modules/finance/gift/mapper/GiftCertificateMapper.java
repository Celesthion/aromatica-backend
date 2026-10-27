package com.aromatica.modules.finance.gift.mapper;

import com.aromatica.modules.finance.gift.dtos.GiftDtos.CertificateCheck;
import com.aromatica.modules.finance.gift.dtos.GiftDtos.GiftCertificateDto;
import com.aromatica.modules.finance.gift.dtos.GiftDtos.GiftStats;
import com.aromatica.modules.finance.gift.dtos.GiftDtos.UseDto;
import com.aromatica.modules.finance.gift.model.GiftCertificate;
import com.aromatica.modules.finance.gift.model.GiftCertificateUse;
import com.aromatica.modules.finance.gift.service.GiftCertificateService;
import com.aromatica.modules.masterdata.party.model.Counterparty;
import com.aromatica.modules.masterdata.party.service.CounterpartyService;
import com.aromatica.security.auth.service.UserDirectory;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Ручной: покупатель и продавец — в других модулях, для списка грузим их пачкой. */
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class GiftCertificateMapper {

    CounterpartyService counterpartyService;
    UserDirectory users;

    public GiftCertificateDto toDto(GiftCertificate c) {
        // у старых бланков, проданных без покупателя, его нет
        Counterparty buyer = c.getBuyerId() != null ? counterpartyService.find(c.getBuyerId()) : null;
        return toDto(c, buyer, c.getSoldBy() != null ? users.name(c.getSoldBy()) : null);
    }

    public List<GiftCertificateDto> toDtos(List<GiftCertificate> certificates) {
        Map<UUID, Counterparty> buyers = counterpartyService.byIds(
                certificates.stream().map(GiftCertificate::getBuyerId).toList());
        Map<UUID, String> sellers = users.names(
                certificates.stream().map(GiftCertificate::getSoldBy).toList());
        return certificates.stream()
                .map(c -> toDto(c, buyers.get(c.getBuyerId()), sellers.get(c.getSoldBy())))
                .toList();
    }

    public UseDto toDto(GiftCertificateUse u) {
        return new UseDto(u.getDocId(), u.getAmountMinor(), u.getAt());
    }

    private static GiftCertificateDto toDto(
            GiftCertificate c,
            Counterparty buyer,
            String sellerName
    ) {
        return new GiftCertificateDto(c.getId(), c.getCode(), c.getNominalMinor(),
                c.spendableMinor(),
                c.getStatus(), c.getComment(), c.getSoldAt(),
                sellerName,
                c.getBuyerId(), buyer != null ? buyer.getName() : null,
                buyer != null ? buyer.getPhone() : null);
    }

    public GiftStats toStats(GiftCertificateService.Stats stats) {
        return new GiftStats(stats.liabilityMinor(), stats.inStock(), stats.total());
    }

    /** Причину отказа отдаём текстом: кассиру не нужно разбираться в статусах. */
    public CertificateCheck toCheck(
            @Nullable GiftCertificate c,
            String code
    ) {
        if (c == null) {
            return new CertificateCheck(false, "Такого сертификата нет — проверьте код",
                    null, code == null ? "" : code.trim(), 0, 0, null);
        }
        String reason = switch (c.getStatus()) {
            case NEW -> "Сертификат ещё не продан";
            case BLOCKED -> "Сертификат заблокирован";
            case USED -> "Сертификат уже потрачен";
            case ACTIVE -> null;
        };
        return new CertificateCheck(reason == null, reason, c.getId(), c.getCode(),
                c.getNominalMinor(), c.getBalanceMinor(), c.getStatus());
    }
}
