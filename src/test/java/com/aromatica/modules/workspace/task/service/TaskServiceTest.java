package com.aromatica.modules.workspace.task.service;

import com.aromatica.modules.workspace.task.dtos.TaskDtos.CreateTaskRequest;
import com.aromatica.modules.workspace.task.event.TaskDoneEvent;
import com.aromatica.modules.workspace.task.mapper.TaskMapper;
import com.aromatica.modules.workspace.task.mapper.TaskMapperImpl;
import com.aromatica.modules.workspace.task.model.Task;
import com.aromatica.modules.workspace.task.repository.TaskItemRepository;
import com.aromatica.modules.workspace.task.repository.TaskRepository;
import com.aromatica.modules.workspace.task.service.impl.TaskServiceImpl;
import com.aromatica.security.auth.model.AppUser;
import com.aromatica.security.auth.service.UserDirectory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Задачи: правила доступа. Данные синтетические. */
@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock
    private TaskRepository taskRepository;
    @Mock
    private TaskItemRepository itemRepository;
    @Mock
    private UserDirectory users;
    @Mock
    private ApplicationEventPublisher events;

    private TaskService service;
    private final TaskMapper mapper = new TaskMapperImpl();

    private final AppUser admin = user("admin", AppUser.Role.ADMIN);
    private final AppUser seller = user("seller", AppUser.Role.SELLER);
    private final AppUser other = user("other", AppUser.Role.SELLER);

    @BeforeEach
    void setUp() {
        service = new TaskServiceImpl(taskRepository, itemRepository, users, events, Clock.systemDefaultZone());
        lenient().when(users.require("admin")).thenReturn(admin);
        lenient().when(users.require("seller")).thenReturn(seller);
        lenient().when(users.require("other")).thenReturn(other);
        lenient().when(taskRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private static AppUser user(
            String login,
            AppUser.Role role
    ) {
        AppUser u = new AppUser();
        u.setId(UUID.randomUUID());
        u.setLogin(login);
        u.setDisplayName(login);
        u.setRole(role);
        return u;
    }

    @Test
    void sellerCreatesTaskForSelfOnly_evenIfAssigneeGiven() {
        Task dto = service.create(mapper.toEntity(new CreateTaskRequest("Протереть полки", null,
                other.getId(), null, List.of("Верхние", "Нижние"))), "seller");

        assertThat(dto.getAssigneeId()).isEqualTo(seller.getId()); // назначено себе, не other
        assertThat(dto.getItems()).hasSize(2);
        assertThat(dto.getStatus()).isEqualTo(Task.Status.TODO);
    }

    @Test
    void create_adminAssignsAnyone() {
        Task dto = service.create(mapper.toEntity(new CreateTaskRequest("Инвентаризация", "до пятницы",
                seller.getId(), null, null)), "admin");

        assertThat(dto.getAssigneeId()).isEqualTo(seller.getId());
        // createdBy заполняет JPA-аудит, а в чистом Mockito-тесте его листенер не работает
    }

    @Test
    void setDone_sellerCannotTouchForeignTask() {
        Task foreign = new Task();
        foreign.setId(UUID.randomUUID());
        foreign.setCreatedBy(admin.getId());
        foreign.setAssigneeId(other.getId()); // назначена другому
        when(taskRepository.require(eq(foreign.getId()), anyString())).thenReturn(foreign);

        assertThatThrownBy(() -> service.setDone(foreign.getId(), true, "seller"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void setDone_assigneeMarksOwnTask() {
        Task mine = new Task();
        mine.setId(UUID.randomUUID());
        mine.setCreatedBy(admin.getId()); // поставил админ
        mine.setAssigneeId(seller.getId()); // мне
        when(taskRepository.require(eq(mine.getId()), anyString())).thenReturn(mine);

        Task dto = service.setDone(mine.getId(), true, "seller");

        assertThat(dto.getStatus()).isEqualTo(Task.Status.DONE);
        assertThat(dto.getDoneAt()).isNotNull();
    }

    @Test
    void repeatedDone_publishesOnceAndKeepsDoneAt() {
        Task mine = new Task();
        mine.setId(UUID.randomUUID());
        mine.setCreatedBy(seller.getId());
        mine.setAssigneeId(seller.getId());
        when(taskRepository.require(eq(mine.getId()), anyString())).thenReturn(mine);

        service.setDone(mine.getId(), true, "seller");
        LocalDateTime firstDoneAt = mine.getDoneAt();
        service.setDone(mine.getId(), true, "seller");

        assertThat(mine.getDoneAt()).isEqualTo(firstDoneAt);
        verify(events, times(1)).publishEvent(any(TaskDoneEvent.class));
    }

    @Test
    void delete_sellerCannotDeleteTaskAssignedButNotCreatedByThem() {
        Task mine = new Task();
        mine.setId(UUID.randomUUID());
        mine.setCreatedBy(admin.getId());
        mine.setAssigneeId(seller.getId());
        when(taskRepository.require(eq(mine.getId()), anyString())).thenReturn(mine);

        assertThatThrownBy(() -> service.delete(mine.getId(), "seller"))
                .isInstanceOf(AccessDeniedException.class);
    }
}
