package com.aromatica.modules.pos.shift.service.impl;

import com.aromatica.modules.pos.shift.model.PosShift;
import com.aromatica.modules.pos.shift.model.QPosShift;
import com.aromatica.modules.pos.shift.repository.PosShiftRepository;
import com.aromatica.modules.pos.shift.search.ShiftSearchRequest;
import com.aromatica.modules.pos.shift.service.ShiftSearchService;
import com.querydsl.core.BooleanBuilder;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ShiftSearchServiceImpl implements ShiftSearchService {

    PosShiftRepository shiftRepository;

    @Override
    public Page<PosShift> search(ShiftSearchRequest request) {
        QPosShift s = QPosShift.posShift;
        BooleanBuilder p = new BooleanBuilder();
        if (request.getStatus() != null) {
            p.and(s.status.eq(request.getStatus()));
        }
        if (request.getFrom() != null) {
            p.and(s.openedAt.goe(request.getFrom()));
        }
        if (request.getTo() != null) {
            p.and(s.openedAt.lt(request.getTo()));
        }
        String q = request.getQ();
        if (q != null && q.trim().matches("\\d{1,18}")) {
            p.and(s.number.eq(Long.parseLong(q.trim())));
        }
        return shiftRepository.findAll(p, request);
    }
}
