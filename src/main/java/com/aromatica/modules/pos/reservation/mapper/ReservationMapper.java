package com.aromatica.modules.pos.reservation.mapper;

import com.aromatica.modules.masterdata.catalog.product.service.ProductService;
import com.aromatica.modules.pos.reservation.dtos.ReservationDtos.CreateReservationRequest;
import com.aromatica.modules.pos.reservation.dtos.ReservationDtos.ReservationDto;
import com.aromatica.modules.pos.reservation.dtos.ReservationDtos.ReservationLineDto;
import com.aromatica.modules.pos.reservation.model.Reservation;
import com.aromatica.modules.pos.reservation.model.ReservationLine;
import com.aromatica.modules.pos.storefront.dtos.StorefrontDtos.PosProduct;
import com.aromatica.modules.pos.storefront.service.PosCatalogQueryService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Ручной маппер: карточки как у витрины, но без фильтра по наличию; имена грузим пачкой на весь список.
 */
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ReservationMapper {

    Clock clock;
    PosCatalogQueryService catalogService;
    ProductService productService;

    /**
     * Только то, что задаёт касса; имя, телефон, статус, срок и номера строк ставит сервис.
     */
    public Reservation toEntity(CreateReservationRequest request) {
        Reservation reservation = new Reservation();
        reservation.setWarehouseId(request.warehouseId());
        reservation.setCounterpartyId(request.counterpartyId());
        reservation.setComment(request.comment());
        request.lines().forEach(l -> {
            ReservationLine line = new ReservationLine();
            line.setProductId(l.productId());
            line.setQty(l.qty());
            line.setPriceMinor(l.priceMinor());
            reservation.getLines().add(line);
        });
        return reservation;
    }

    public ReservationDto toDto(Reservation reservation) {
        return toDtos(List.of(reservation), reservation.getWarehouseId()).getFirst();
    }

    public List<ReservationDto> toDtos(
            List<Reservation> reservations,
            UUID warehouseId
    ) {
        Set<UUID> productIds = new HashSet<>();
        reservations.forEach(r -> r.getLines().forEach(l -> productIds.add(l.getProductId())));
        Map<UUID, PosProduct> cards = catalogService.cardsByIds(productIds, warehouseId).stream()
                .collect(Collectors.toMap(PosProduct::id, Function.identity()));
        Map<UUID, String> names = productService.names(productIds);
        LocalDateTime now = LocalDateTime.now(clock);
        return reservations.stream().map(r -> toDto(r, cards, names, now)).toList();
    }

    private ReservationDto toDto(
            Reservation r,
            Map<UUID, PosProduct> cards,
            Map<UUID, String> names,
            LocalDateTime now
    ) {
        // собственный резерв брони прибавляем к доступному: продавать её саму он не мешает
        Map<UUID, BigDecimal> own = r.reservedQty(now);
        List<ReservationLineDto> lines = r.getLines().stream()
                .map(l -> new ReservationLineDto(
                        l.getProductId(),
                        names.getOrDefault(l.getProductId(), l.getProductId().toString()),
                        l.getQty(), l.getPriceMinor(),
                        withOwnReserve(cards.get(l.getProductId()), own.get(l.getProductId()))))
                .toList();
        return new ReservationDto(r.getId(), r.getCustomerName(), r.getPhone(), r.getCounterpartyId(), r.getComment(),
                r.getExpiresAt(), r.isExpired(now), r.getStatus(), r.sumMinor(), lines);
    }

    private static PosProduct withOwnReserve(
            PosProduct card,
            BigDecimal ownQty
    ) {
        if (card == null || ownQty == null) {
            return card;
        }
        return card.plusQty(ownQty);
    }
}
