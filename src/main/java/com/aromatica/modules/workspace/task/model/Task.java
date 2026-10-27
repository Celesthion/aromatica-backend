package com.aromatica.modules.workspace.task.model;

import com.aromatica.common.audit.Auditable;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Задача сотрудника с необязательным чек-листом (task_item). */
@Entity
@Table(name = "task")
@Getter
@Setter
@NoArgsConstructor
public class Task extends Auditable {

    public enum Status { TODO, DONE }

    @Id
    private UUID id;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    /** null — задача без исполнителя, в общем пуле. Такие ставит только админ. */
    @Column(name = "assignee_id")
    private UUID assigneeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.TODO;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "done_at")
    private LocalDateTime doneAt;

    @OneToMany(mappedBy = "task", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<TaskItem> items = new ArrayList<>();

    /** false — статус уже такой, ничего не менялось. */
    public boolean markDone(
            boolean done,
            LocalDateTime now
    ) {
        Status target = done ? Status.DONE : Status.TODO;
        if (status == target) {
            return false;
        }
        status = target;
        doneAt = done ? now : null;
        return true;
    }
}
