package com.aromatica.common.persistence;

import com.aromatica.common.error.NotFoundException;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

/** Общий предок репозиториев: загрузка по id с 404, если записи нет. */
@NoRepositoryBean
public interface EntityRepository<T, ID> extends JpaRepository<T, ID> {

    /** Запись по id или NotFoundException с текстом «{notFound}: {id}». */
    default T require(ID id, String notFound) {
        return findById(id).orElseThrow(() -> new NotFoundException(notFound + ": " + id));
    }
}
