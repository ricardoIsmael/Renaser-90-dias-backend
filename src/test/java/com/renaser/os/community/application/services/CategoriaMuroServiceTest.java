package com.renaser.os.community.application.services;

import com.renaser.os.community.application.ports.in.categoria.CrearCategoriaMuroUseCase.CrearCategoriaMuroCommand;
import com.renaser.os.community.application.ports.in.categoria.EliminarCategoriaMuroUseCase.EliminarCategoriaMuroCommand;
import com.renaser.os.community.application.ports.in.categoria.ReordenarCategoriasMuroUseCase.ReordenarCategoriasMuroCommand;
import com.renaser.os.community.application.ports.out.categoria.EliminarCategoriaMuroPort;
import com.renaser.os.community.application.ports.out.categoria.LoadCategoriaMuroPort;
import com.renaser.os.community.application.ports.out.categoria.SaveCategoriaMuroPort;
import com.renaser.os.community.domain.model.categoria.CategoriaMuro;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoriaMuroServiceTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-24T10:00:00Z"));

    @Mock
    private LoadCategoriaMuroPort loadPort;
    @Mock
    private SaveCategoriaMuroPort savePort;
    @Mock
    private EliminarCategoriaMuroPort eliminarPort;
    @Mock
    private UserSummaryFinder userSummaryFinder;

    private CategoriaMuroService service;

    private final UserId admin = UserId.of(UUID.randomUUID());
    private final UserId mentor = UserId.of(UUID.randomUUID());

    @BeforeEach
    void setUp() {
        service = new CategoriaMuroService(loadPort, savePort, eliminarPort, userSummaryFinder, CLOCK);
        lenient().when(userSummaryFinder.findById(admin))
                .thenReturn(Optional.of(new UserSummary(admin, "Admin", null, UserRole.ADMIN, UserStatus.ACTIVE)));
        lenient().when(userSummaryFinder.findById(mentor))
                .thenReturn(Optional.of(new UserSummary(mentor, "Mentor", null, UserRole.MENTOR, UserStatus.ACTIVE)));
    }

    @Test
    void crearComoMentorEsRechazado() {
        var command = new CrearCategoriaMuroCommand(mentor, "LOGROS", "Logros", "🏆");
        assertThatThrownBy(() -> service.crear(command)).isInstanceOf(NotAuthorizedException.class);
        verify(savePort, never()).save(any());
    }

    @Test
    void crearConClaveYaExistenteFalla() {
        when(loadPort.porClave("LOGROS")).thenReturn(Optional.of(
                CategoriaMuro.rehydrate("LOGROS", "Logros", "🏆", 1, true, false, CLOCK.now(), CLOCK.now())));
        var command = new CrearCategoriaMuroCommand(admin, "LOGROS", "Logros", "🏆");
        assertThatThrownBy(() -> service.crear(command)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void eliminarUnaDeSistemaFalla() {
        when(loadPort.porClave("PRESENTACION")).thenReturn(Optional.of(CategoriaMuro.rehydrate("PRESENTACION",
                "Presentacion", "👋", 5, true, true, CLOCK.now(), CLOCK.now())));
        var command = new EliminarCategoriaMuroCommand(admin, "PRESENTACION");
        assertThatThrownBy(() -> service.eliminar(command)).isInstanceOf(IllegalArgumentException.class);
        verify(eliminarPort, never()).eliminar(any());
    }

    @Test
    void eliminarUnaConPublicacionesFalla() {
        when(loadPort.porClave("LOGROS")).thenReturn(Optional.of(
                CategoriaMuro.rehydrate("LOGROS", "Logros", "🏆", 1, true, false, CLOCK.now(), CLOCK.now())));
        when(loadPort.contarPublicaciones("LOGROS")).thenReturn(3);
        var command = new EliminarCategoriaMuroCommand(admin, "LOGROS");
        assertThatThrownBy(() -> service.eliminar(command)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void reordenarConClaveDesconocidaFalla() {
        when(loadPort.listarClaves()).thenReturn(Set.of("LOGROS", "AYUDA"));
        var command = new ReordenarCategoriasMuroCommand(admin, List.of("LOGROS", "INEXISTENTE"));
        assertThatThrownBy(() -> service.reordenar(command)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void listarPublicasNoRequiereRolAlguno() {
        when(loadPort.listarActivas()).thenReturn(List.of(
                CategoriaMuro.rehydrate("LOGROS", "Logros", "🏆", 1, true, false, CLOCK.now(), CLOCK.now())));
        assertThat(service.listarPublicas()).hasSize(1);
    }
}
