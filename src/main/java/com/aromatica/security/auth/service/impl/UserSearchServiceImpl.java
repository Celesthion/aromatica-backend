package com.aromatica.security.auth.service.impl;

import com.aromatica.security.auth.model.AppUser;
import com.aromatica.security.auth.model.QAppUser;
import com.aromatica.security.auth.repository.AppUserRepository;
import com.aromatica.security.auth.search.UserSearchRequest;
import com.aromatica.security.auth.service.UserSearchService;
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
public class UserSearchServiceImpl implements UserSearchService {

    AppUserRepository repository;

    @Override
    public Page<AppUser> search(UserSearchRequest request) {
        QAppUser u = QAppUser.appUser;
        BooleanBuilder p = new BooleanBuilder();
        if (!Boolean.TRUE.equals(request.getIncludeArchived())) {
            p.and(u.archived.isFalse());
        }
        if (request.getRole() != null) {
            p.and(u.role.eq(request.getRole()));
        }
        String q = request.getQ();
        if (q != null && !q.isBlank()) {
            String t = q.trim();
            p.and(u.login.containsIgnoreCase(t).or(u.displayName.containsIgnoreCase(t)));
        }
        return repository.findAll(p, request);
    }
}
