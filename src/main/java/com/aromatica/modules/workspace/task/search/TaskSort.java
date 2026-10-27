package com.aromatica.modules.workspace.task.search;

import com.aromatica.common.search.SortBy;
import com.aromatica.modules.workspace.task.model.QTask;
import com.aromatica.modules.workspace.task.model.Task;
import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.NumberExpression;
import org.springframework.data.domain.Sort;
import org.springframework.data.querydsl.QSort;

public enum TaskSort implements SortBy {

    /** Открытые сверху, ближайший срок первым, без срока — в конец. Направление не меняет порядок. */
    URGENT {
        @Override
        public Sort getSorting(Sort.Direction direction) {
            QTask t = QTask.task;
            // статус — строка, по алфавиту DONE шёл бы раньше TODO
            NumberExpression<Integer> doneLast = new CaseBuilder()
                    .when(t.status.eq(Task.Status.TODO)).then(0).otherwise(1);
            return new QSort(new OrderSpecifier<>(Order.ASC, doneLast),
                    new OrderSpecifier<>(Order.ASC, t.dueDate, OrderSpecifier.NullHandling.NullsLast),
                    new OrderSpecifier<>(Order.DESC, t.createdAt));
        }
    },
    CREATED {
        @Override
        public Sort getSorting(Sort.Direction direction) {
            return new QSort(new OrderSpecifier<>(directionToOrder(direction), QTask.task.createdAt));
        }
    }
}
