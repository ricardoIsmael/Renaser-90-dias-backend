package com.renaser.os.habits.application.services;

import com.renaser.os.habits.application.ports.in.habitoadmin.ActualizarHabitoUseCase.ActualizarHabitoCommand;
import com.renaser.os.habits.application.ports.in.habitoadmin.CambiarActivoHabitoUseCase.CambiarActivoHabitoCommand;
import com.renaser.os.habits.application.ports.in.habitoadmin.CrearHabitoUseCase.CrearHabitoCommand;
import com.renaser.os.habits.application.ports.in.habitoadmin.EliminarHabitoUseCase.EliminarHabitoCommand;
import com.renaser.os.habits.application.ports.out.habito.LoadHabitoPort;
import com.renaser.os.habits.application.ports.out.habito.SaveHabitoPort;
import com.renaser.os.habits.domain.model.habito.DetallesHabito;
import com.renaser.os.habits.domain.model.habito.ExigenciaEvidencia;
import com.renaser.os.habits.domain.model.habito.Habito;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.habito.TipoHabito;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.IdGenerator;
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
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HabitoAdminServiceTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-26T10:00:00Z"));
    /** Identidad fija: con el id entrando por el puerto IdGenerator, crearDeSistema() ya no lo sortea. */
    private static final UUID ID_GENERADO = UUID.fromString("00000000-0000-4000-8000-000000000001");

    @Mock
    private LoadHabitoPort loadPort;
    @Mock
    private SaveHabitoPort savePort;
    @Mock
    private UserSummaryFinder userSummaryFinder;
    @Mock
    private IdGenerator idGenerator;

    private HabitoAdminService service;

    private final UserId admin = UserId.of(UUID.randomUUID());
    private final UserId alchemist = UserId.of(UUID.randomUUID());
    private final UserId mentor = UserId.of(UUID.randomUUID());
    private final UserId suspendedAdmin = UserId.of(UUID.randomUUID());

    @BeforeEach
    void setUp() {
        service = new HabitoAdminService(loadPort, savePort, new HabitoAdminGuard(userSummaryFinder), CLOCK,
                idGenerator);
        lenient().when(idGenerator.newId()).thenReturn(ID_GENERADO);
        lenient().when(userSummaryFinder.findById(admin))
                .thenReturn(Optional.of(new UserSummary(admin, "Admin", null, UserRole.ADMIN, UserStatus.ACTIVE)));
        lenient().when(userSummaryFinder.findById(alchemist)).thenReturn(
                Optional.of(new UserSummary(alchemist, "Alquimista", null, UserRole.ALCHEMIST, UserStatus.ACTIVE)));
        lenient().when(userSummaryFinder.findById(mentor))
                .thenReturn(Optional.of(new UserSummary(mentor, "Mentor", null, UserRole.MENTOR, UserStatus.ACTIVE)));
        lenient().when(userSummaryFinder.findById(suspendedAdmin)).thenReturn(Optional.of(
                new UserSummary(suspendedAdmin, "Admin suspendido", null, UserRole.ADMIN, UserStatus.SUSPENDED)));
        lenient().when(savePort.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private static DetallesHabito detalles() {
        return new DetallesHabito("desc", "CUERPO", ExigenciaEvidencia.OPCIONAL, false, false);
    }

    @Test
    void crearComoMentorEsRechazado() {
        var command = new CrearHabitoCommand(mentor, "Titulo", TipoHabito.CHECKBOX, detalles());
        assertThatThrownBy(() -> service.crear(command)).isInstanceOf(NotAuthorizedException.class);
        verify(savePort, never()).save(any());
    }

    @Test
    void crearComoAdminSuspendidoEsRechazado() {
        var command = new CrearHabitoCommand(suspendedAdmin, "Titulo", TipoHabito.CHECKBOX, detalles());
        assertThatThrownBy(() -> service.crear(command)).isInstanceOf(NotAuthorizedException.class);
        verify(savePort, never()).save(any());
    }

    @Test
    void crearComoAlchemistFunciona() {
        var command = new CrearHabitoCommand(alchemist, "Titulo", TipoHabito.CHECKBOX, detalles());

        Habito creado = service.crear(command);

        assertThat(creado.titulo()).isEqualTo("Titulo");
        assertThat(creado.esDeSistema()).isTrue();
        assertThat(creado.claveSistema()).isNull();
        verify(savePort).save(any());
    }

    @Test
    void actualizarSobreHabitoInexistenteFalla404() {
        HabitoId id = HabitoId.of(UUID.randomUUID());
        when(loadPort.byId(id)).thenReturn(Optional.empty());
        var command = new ActualizarHabitoCommand(admin, id, detalles());

        assertThatThrownBy(() -> service.actualizar(command)).isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void actualizarNoCambiaElTipoNiLaClaveSistema() {
        Habito existente = Habito.crearDeSistema(HabitoId.of(UUID.randomUUID()), "Titulo", TipoHabito.BLOQUEO,
                detalles(), CLOCK.now());
        HabitoId id = existente.id();
        when(loadPort.byId(id)).thenReturn(Optional.of(existente));
        var nuevosDetalles = new DetallesHabito("otra", "MENTE", ExigenciaEvidencia.OBLIGATORIA, true, true);

        Habito actualizado = service.actualizar(new ActualizarHabitoCommand(admin, id, nuevosDetalles));

        assertThat(actualizado.tipo()).isEqualTo(TipoHabito.BLOQUEO);
        assertThat(actualizado.claveSistema()).isNull();
        assertThat(actualizado.categoriaClave()).isEqualTo("MENTE");
    }

    @Test
    void cambiarActivoDesactivaYReactiva() {
        Habito existente = Habito.crearDeSistema(HabitoId.of(UUID.randomUUID()), "Titulo", TipoHabito.CHECKBOX,
                detalles(), CLOCK.now());
        HabitoId id = existente.id();
        when(loadPort.byId(id)).thenReturn(Optional.of(existente));

        Habito desactivado = service.cambiarActivo(new CambiarActivoHabitoCommand(admin, id, false));
        assertThat(desactivado.activo()).isFalse();

        Habito reactivado = service.cambiarActivo(new CambiarActivoHabitoCommand(admin, id, true));
        assertThat(reactivado.activo()).isTrue();
    }

    @Test
    void eliminarComoMentorEsRechazadoSinTocarElPuerto() {
        var command = new EliminarHabitoCommand(mentor, HabitoId.of(UUID.randomUUID()));
        assertThatThrownBy(() -> service.eliminar(command)).isInstanceOf(NotAuthorizedException.class);
        verify(savePort, never()).eliminar(any());
    }

    @Test
    void eliminarSobreHabitoInexistenteFalla404() {
        HabitoId id = HabitoId.of(UUID.randomUUID());
        when(loadPort.byId(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.eliminar(new EliminarHabitoCommand(admin, id)))
                .isInstanceOf(NoSuchElementException.class);
        verify(savePort, never()).eliminar(any());
    }

    @Test
    void listarComoMentorEsRechazado() {
        assertThatThrownBy(() -> service.listar(mentor)).isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    void listarComoAdminDevuelveElCatalogoCompleto() {
        when(loadPort.catalogoCompleto()).thenReturn(
                java.util.List.of(Habito.crearDeSistema(HabitoId.of(UUID.randomUUID()), "A", TipoHabito.CHECKBOX,
                        detalles(), CLOCK.now())));

        assertThat(service.listar(admin)).hasSize(1);
    }
}
