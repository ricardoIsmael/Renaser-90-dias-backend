package com.renaser.os.users.application.services;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.FichaDeMentorFinder.FichaDeMentor;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.application.ports.out.mentorprofile.LoadMentorProfilePort;
import com.renaser.os.users.application.ports.out.user.LoadUserPort;
import com.renaser.os.users.domain.model.user.Email;
import com.renaser.os.users.domain.model.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** El cuerpo de mentores como lo lee el Líder: los de cuenta activa y, aparte, los de cuenta suspendida (spec 056 FR-004). */
class FichaDeMentorFinderServiceTest {

    private final LoadUserPort usuarios = mock(LoadUserPort.class);
    private final LoadMentorProfilePort perfiles = mock(LoadMentorProfilePort.class);
    private final FichaDeMentorFinderService finder = new FichaDeMentorFinderService(usuarios, perfiles);

    private static User mentor(String nombre, UserRole rol, UserStatus estado) {
        UserId id = UserId.of(UUID.randomUUID());
        return User.rehydrate(id, new Email(id.value() + "@renaser.test"), rol, estado, nombre, null, null, null, null);
    }

    // spec 056 FR-004
    @Test
    @DisplayName("spec 056 FR-004: los suspendidos se piden a la base con la cuenta SUSPENDED y los mismos roles de mentor, y salen por nombre")
    void soloLosSuspendidos() {
        when(usuarios.byRoles(argThat(roles -> UserRole.ROLES_QUE_ACTUAN_COMO_MENTOR.containsAll(roles)
                        && roles.containsAll(UserRole.ROLES_QUE_ACTUAN_COMO_MENTOR)), eq(UserStatus.SUSPENDED), eq(0), anyInt()))
                .thenReturn(List.of(mentor("Raúl Soto", UserRole.MENTOR, UserStatus.SUSPENDED),
                        mentor("ana guía", UserRole.TRAINEE_GUIDE, UserStatus.SUSPENDED)));

        List<FichaDeMentor> suspendidos = finder.mentoresSuspendidos();

        assertThat(suspendidos).extracting(FichaDeMentor::nombreCompleto).containsExactly("ana guía", "Raúl Soto");
        assertThat(suspendidos).extracting(FichaDeMentor::estado).containsOnly(UserStatus.SUSPENDED);
        verify(usuarios, never()).byRoles(argThat(roles -> true), eq(UserStatus.ACTIVE), anyInt(), anyInt());
    }

    // spec 056 FR-004
    @Test
    @DisplayName("spec 056 FR-004: los activos siguen pidiéndose solo con la cuenta ACTIVE: un suspendido nunca se cuela entre ellos")
    void soloLosActivos() {
        when(usuarios.byRoles(argThat(roles -> true), eq(UserStatus.ACTIVE), eq(0), anyInt()))
                .thenReturn(List.of(mentor("Luisa Rojas", UserRole.MENTOR, UserStatus.ACTIVE)));

        assertThat(finder.mentoresActivos()).extracting(FichaDeMentor::nombreCompleto).containsExactly("Luisa Rojas");
        verify(usuarios, never()).byRoles(argThat(roles -> true), eq(UserStatus.SUSPENDED), anyInt(), anyInt());
    }
}
