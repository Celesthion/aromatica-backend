package com.aromatica.modules.inventory.doc.service.impl;

import com.aromatica.modules.inventory.doc.model.SalesCountRow;
import com.aromatica.modules.inventory.doc.model.SalesGroupRow;
import com.aromatica.modules.inventory.doc.model.SellerStatsRow;
import com.aromatica.modules.inventory.doc.repository.StockDocRepository;
import com.aromatica.modules.inventory.doc.service.SalesStatsService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SalesStatsServiceImpl implements SalesStatsService {

    StockDocRepository docRepository;

    @Override
    public SellerStatsRow sellerStats(UUID userId) {
        return docRepository.sellerStats(userId);
    }

    @Override
    public List<SalesGroupRow> salesGroups(
            LocalDateTime from,
            LocalDateTime to
    ) {
        return docRepository.salesGroups(from, to);
    }

    @Override
    public List<SalesCountRow> salesCounts(
            LocalDateTime from,
            LocalDateTime to
    ) {
        return docRepository.salesCounts(from, to);
    }
}
