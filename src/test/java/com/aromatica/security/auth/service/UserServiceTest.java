package com.aromatica.security.auth.service;

import com.aromatica.common.error.BusinessRuleException;
import com.aromatica.common.error.ConflictException;
import com.aromatica.security.auth.model.AppUser;
import com.aromatica.security.auth.repository.AppUserRepository;
import com.aromatica.security.auth.service.impl.UserServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Сотрудники: пароль, архив, последний администратор. Данные синтетические. */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final String STRONG = "Secret-123";

    @Mock
    private AppUserRepository repository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private RefreshTokenService refreshTokens;

    private UserService service;

    @BeforeEach
    void setUp() {
        service = new UserServiceImpl(repository, passwordEncoder, refreshTokens);
        lenient().when(passwordEncoder.encode(any())).thenReturn("hash");
    }

    private AppUser user(
            String login,
            AppUser.Role role
    ) {
        AppUser u = new AppUser();
        u.setId(UUID.randomUUID());
        u.setLogin(login);
        u.setRole(role);
        u.setPasswordHash("old-hash");
        lenient().when(repository.require(eq(u.getId()), anyString())).thenReturn(u);
        lenient().when(repository.findByLogin(login)).thenReturn(Optional.of(u));
        return u;
    }

    @Test
    void changeOwnPassword_revokesSessions() {
        AppUser seller = user("seller", AppUser.Role.SELLER);
        when(passwordEncoder.matches("current", "old-hash")).thenReturn(true);

        service.changeOwnPassword("seller", "current", STRONG);

        assertThat(seller.getPasswordHash()).isEqualTo("hash");
        verify(refreshTokens).revokeAll(seller.getId());
    }

    @Test
    void changeOwnPassword_wrongCurrent_keepsSessions() {
        AppUser seller = user("seller", AppUser.Role.SELLER);
        when(passwordEncoder.matches("wrong", "old-hash")).thenReturn(false);

        assertThatThrownBy(() -> service.changeOwnPassword("seller", "wrong", STRONG))
                .isInstanceOf(BusinessRuleException.class);
        verify(refreshTokens, never()).revokeAll(any());
    }

    @Test
    void adminSetsPassword_revokesSessions() {
        AppUser seller = user("seller", AppUser.Role.SELLER);

        service.resetPassword(seller.getId(), STRONG);

        assertThat(seller.getPasswordHash()).isEqualTo("hash");

        verify(refreshTokens).revokeAll(seller.getId());
    }

    @Test
    void updateWithoutPassword_keepsSessions() {
        AppUser seller = user("seller", AppUser.Role.SELLER);

        AppUser changes = new AppUser();
        changes.setDisplayName("Новое имя");

        service.update(seller.getId(), changes);

        assertThat(seller.getDisplayName()).isEqualTo("Новое имя");
        assertThat(seller.getRole()).isEqualTo(AppUser.Role.SELLER); // роль не прислали — прежняя
        verify(refreshTokens, never()).revokeAll(any());
    }

    @Test
    void archive_revokesSessions_restoreDoesNot() {
        AppUser seller = user("seller", AppUser.Role.SELLER);

        service.setArchived(seller.getId(), true, "admin");
        assertThat(seller.isArchived()).isTrue();
        verify(refreshTokens).revokeAll(seller.getId());

        // повторная архивация — без изменений: двойной клик не возвращает сотрудника из архива
        service.setArchived(seller.getId(), true, "admin");
        assertThat(seller.isArchived()).isTrue();

        service.setArchived(seller.getId(), false, "admin");
        assertThat(seller.isArchived()).isFalse();
        verify(refreshTokens).revokeAll(seller.getId()); // по-прежнему один раз
    }

    @Test
    void archive_rejectsSelf() {
        AppUser admin = user("admin", AppUser.Role.ADMIN);

        assertThatThrownBy(() -> service.setArchived(admin.getId(), true, "admin"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("самого себя");
    }

    @Test
    void update_rejectsDemotingLastAdmin() {
        AppUser admin = user("boss", AppUser.Role.ADMIN);
        when(repository.existsByRoleAndArchivedFalseAndIdNot(eq(AppUser.Role.ADMIN), eq(admin.getId())))
                .thenReturn(false);
        AppUser changes = new AppUser();
        changes.setRole(AppUser.Role.SELLER);

        assertThatThrownBy(() -> service.update(admin.getId(), changes))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("последнего администратора");
        assertThat(admin.getRole()).isEqualTo(AppUser.Role.ADMIN);
    }

    @Test
    void archive_rejectsLastAdmin() {
        AppUser admin = user("boss", AppUser.Role.ADMIN);
        when(repository.existsByRoleAndArchivedFalseAndIdNot(eq(AppUser.Role.ADMIN), eq(admin.getId())))
                .thenReturn(false);

        assertThatThrownBy(() -> service.setArchived(admin.getId(), true, "other-admin"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("последнего администратора");
        assertThat(admin.isArchived()).isFalse();
    }
}
