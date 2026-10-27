package com.aromatica.modules.inventory.report.service.impl;

import com.aromatica.common.search.PageMapper;
import com.aromatica.common.search.PageResponse;
import com.aromatica.modules.inventory.report.dtos.StockDtos.StockRow;
import com.aromatica.modules.inventory.report.dtos.StockDtos.StockSummary;
import com.aromatica.modules.inventory.report.mapper.StockMapper;
import com.aromatica.modules.inventory.report.search.StockSearchRequest;
import com.aromatica.modules.inventory.report.service.StockQueryService;
import com.aromatica.modules.inventory.stock.model.QStockBalance;
import com.aromatica.modules.inventory.stock.model.StockLevel;
import com.aromatica.modules.inventory.warehouse.service.WarehouseLookup;
import com.aromatica.modules.masterdata.catalog.product.model.CatalogProduct;
import com.aromatica.modules.masterdata.catalog.product.model.QCatalogProduct;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.Expression;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.querydsl.QSort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.aromatica.common.util.Strings.blankToNull;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class StockQueryServiceImpl implements StockQueryService {

    WarehouseLookup warehouseLookup;
    PageMapper pageMapper;
    StockMapper stockMapper;
    JPAQueryFactory queryFactory;
    @Override
    public PageResponse<StockRow> search(StockSearchRequest request) {
        QStockBalance b = QStockBalance.stockBalance;
        QCatalogProduct p = QCatalogProduct.catalogProduct;
        BooleanBuilder where = onWarehouse(request.getWarehouseId());
        String q = blankToNull(request.getQ());
        if (q != null) {
            where.and(p.name.containsIgnoreCase(q));
        }
        PageRequest page = PageRequest.of(request.getPage(), request.getSize());
        if (request.getProductIds() != null) {
            if (request.getProductIds().isEmpty()) {
                return pageMapper.toResponse(Page.empty(page), List.of());
            }
            where.and(b.productId.in(request.getProductIds()));
        }
        List<OrderSpecifier<?>> order = new ArrayList<>(
                ((QSort) request.getSortBy().getSorting(request.getDirection())).getOrderSpecifiers());
        // при равном ключе порядок задаёт id позиции, иначе страницы при листании «прыгают»
        order.add(b.productId.asc());

        List<Tuple> found = queryFactory.select(b, p.name, p.kind, p.unit)
                .from(b, p)
                .where(where)
                .orderBy(order.toArray(OrderSpecifier[]::new))
                .offset(page.getOffset())
                .limit(page.getPageSize())
                .fetch();
        Long total = queryFactory.select(b.count()).from(b, p).where(where).fetchOne();
        List<StockRow> rows = found.stream()
                .map(t -> stockMapper.toRow(t.get(b), t.get(p.name), t.get(p.kind), t.get(p.unit)))
                .toList();
        return pageMapper.toResponse(new PageImpl<>(rows, page, total != null ? total : 0), rows);
    }

    @Override
    public StockSummary summary(UUID warehouse) {
        QStockBalance b = QStockBalance.stockBalance;
        QCatalogProduct p = QCatalogProduct.catalogProduct;
        // те же пороги, что у строки (StockLevel), только посчитанные в базе по всему складу
        NumberExpression<BigDecimal> lowLimit = new CaseBuilder()
                .when(p.unit.eq(CatalogProduct.Unit.ML)).then(StockLevel.LOW_ML)
                .otherwise(StockLevel.LOW_PCS);
        NumberExpression<Integer> isOut = new CaseBuilder()
                .when(b.qty.loe(BigDecimal.ZERO)).then(1).otherwise(0);
        NumberExpression<Integer> isLow = new CaseBuilder()
                .when(b.qty.gt(BigDecimal.ZERO).and(b.qty.loe(lowLimit))).then(1).otherwise(0);
        NumberExpression<Long> positionCount = b.count();
        NumberExpression<Long> outCount = isOut.sumLong();
        NumberExpression<Long> lowCount = isLow.sumLong();
        NumberExpression<BigDecimal> stockValue = b.qty.multiply(b.avgCostMinor).sumBigDecimal();
        Tuple t = queryFactory.select(positionCount, outCount, lowCount, stockValue)
                .from(b, p)
                .where(onWarehouse(warehouse))
                .fetchOne();
        long positions = valueOr(t, positionCount, 0L);
        long out = valueOr(t, outCount, 0L);
        BigDecimal value = valueOr(t, stockValue, BigDecimal.ZERO);
        return new StockSummary(positions, positions - out, valueOr(t, lowCount, 0L), out,
                value.setScale(0, RoundingMode.HALF_UP).longValueExact());
    }

    /** Остатки склада (по умолчанию — основного), соединённые с позицией каталога. */
    private BooleanBuilder onWarehouse(UUID warehouse) {
        QStockBalance b = QStockBalance.stockBalance;
        UUID warehouseId = warehouse != null ? warehouse : defaultWarehouseId();
        return new BooleanBuilder(b.warehouseId.eq(warehouseId))
                .and(QCatalogProduct.catalogProduct.id.eq(b.productId));
    }

    /** Значение колонки агрегата; на пустом складе sum даёт null. */
    private static <T> T valueOr(
            Tuple t,
            Expression<T> column,
            T fallback
    ) {
        T value = t != null ? t.get(column) : null;
        return value != null ? value : fallback;
    }

    private UUID defaultWarehouseId() {
        return warehouseLookup.defaultWarehouseId();
    }
}
