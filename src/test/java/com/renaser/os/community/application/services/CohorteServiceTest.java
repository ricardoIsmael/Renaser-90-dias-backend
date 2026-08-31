package com.renaser.os.community.application.services;

import com.renaser.os.community.application.ports.in.cohorte.ActualizarCohorteUseCase.ActualizarCohorteCommand;
import com.renaser.os.community.application.ports.in.cohorte.CambiarEstadoCohorteUseCase.CambiarEstadoCohorteCommand;
import com.renaser.os.community.application.ports.in.cohorte.CrearCohorteUseCase.CrearCohorteCommand;
import com.renaser.os.community.application.ports.in.cohorte.EliminarCohorteUseCase.EliminarCohorteCommand;
import com.renaser.os.community.application.ports.out.celula.LoadCelulaPort;
import com.renaser.os.community.application.ports.out.cohorte.EliminarCohortePort;
import com.renaser.os.community.application.ports.out.cohorte.LoadCohortePort;
import com.renaser.os.community.application.ports.out.cohorte.SaveCohortePort;
import com.renaser.os.community.domain.model.celula.Celula;
import com.renaser.os.community.domain.model.celula.CelulaId;
import com.renaser.os.community.domain.model.cohorte.Cohorte;
import com.renaser.os.community.domain.model.cohorte.CohorteId;
import com.renaser.os.community.domain.model.cohorte.EstadoCohorte;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
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
class CohorteServiceTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-24T10:00:00Z"));

    @Mock
    private LoadCohortePort loadCohortePort;
    @Mock
    private SaveCohortePort saveCohortePort;
    @Mock
    private EliminarCohortePort eliminarCohortePort;
    @Mock
    private LoadCelulaPort loadCelulaPort;
    @Mock
    private UserSummaryFinder userSummaryFinder;

    private CohorteService service;

    private final UserId admin = UserId.of(UUID.randomUUID());
    private final UserId trainee = UserId.of(UUID.randomUUID());
    private final UserId mentor = UserId.of(UUID.randomUUID());
    private final UserId adminSuspendido = UserId.of(UUID.randomUUID());

    @BeforeEach
    void setUp() {
        service = new CohorteService(loadCohortePort, saveCohortePort, eliminarCohortePort, loadCelulaPort,
                userSummaryFinder, CLOCK);
        lenient().when(userSummaryFinder.findById(admin))
                .thenReturn(Optional.of(new UserSummary(admin, "Admin", null, UserRole.ADMIN, UserStatus.ACTIVE)));
        lenient().when(userSummaryFinder.findById(trainee))
                .thenReturn(Optional.of(new UserSummary(trainee, "Aprendiz", null, UserRole.TRAINEE, UserStatus.ACTIVE)));
        lenient().when(userSummaryFinder.findById(mentor))
                .thenReturn(Optional.of(new UserSummary(mentor, "Mentor", null, UserRole.MENTOR, UserStatus.ACTIVE)));
        lenient().when(userSummaryFinder.findById(adminSuspendido)).thenReturn(Optional.of(new UserSummary(
                adminSuspendido, "Admin suspendido", null, UserRole.ADMIN, UserStatus.SUSPENDED)));
    }

    @Test
    void crearComoTraineeEsRechazado() {
        var command = new CrearCohorteCommand(trainee, "Cohorte Agosto", LocalDate.of(2026, 8, 1), null);
        assertThatThrownBy(() -> service.crear(command)).isInstanceOf(NotAuthorizedException.class);
        verify(saveCohortePort, never()).save(any());
    }

    @Test
    void crearComoAdminGuarda() {
        when(saveCohortePort.save(any())).thenAnswer(inv -> inv.getArgument(0));
        var command = new CrearCohorteCommand(admin, "Cohorte Agosto", LocalDate.of(2026, 8, 1), null);
        Cohorte guardada = service.crear(command).cohorte();
        assertThat(guardada.estado()).isEqualTo(EstadoCohorte.PLANIFICADA);
    }

    @Test
    void actorSuspendidoEsRechazado() {
        UserId suspendido = UserId.of(UUID.randomUUID());
        when(userSummaryFinder.findById(suspendido))
                .thenReturn(Optional.of(new UserSummary(suspendido, "X", null, UserRole.ADMIN, UserStatus.SUSPENDED)));
        var command = new CrearCohorteCommand(suspendido, "Cohorte", LocalDate.now(), null);
        assertThatThrownBy(() -> service.crear(command)).isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    void eliminarConCelulasAsociadasFalla() {
        when(loadCohortePort.contarCelulas(any())).thenReturn(2);
        var command = new EliminarCohorteCommand(admin, CohorteId.newId());
        assertThatThrownBy(() -> service.eliminar(command)).isInstanceOf(IllegalStateException.class);
        verify(eliminarCohortePort, never()).eliminar(any());
    }

    @Test
    void cambiarEstadoSaltandoUnPasoFalla() {
        CohorteId id = CohorteId.newId();
        when(loadCohortePort.porId(id)).thenReturn(Optional.of(Cohorte.rehydrate(id, "Cohorte", LocalDate.now(),
                null, EstadoCohorte.PLANIFICADA, CLOCK.now(), CLOCK.now())));
        var command = new CambiarEstadoCohorteCommand(admin, id, EstadoCohorte.COMPLETADA);
        assertThatThrownBy(() -> service.cambiarEstado(command)).isInstanceOf(IllegalArgumentException.class);
    }

    // ─── CLAUDE.MD sec. 0.3: 403 por rol y 403 por cuenta SUSPENDIDA, metodo por metodo ──

    @Test
    @DisplayName("actualizar(): rol sin permiso (TRAINEE) -> 403, nunca guarda")
    void actualizarComoTraineeEsRechazado() {
        var command = new ActualizarCohorteCommand(trainee, CohorteId.newId(), "Otro", null, null, false);
        assertThatThrownBy(() -> service.actualizar(command)).isInstanceOf(NotAuthorizedException.class);
        verify(saveCohortePort, never()).save(any());
    }

    @Test
    @DisplayName("cambiarEstado(): rol sin permiso (TRAINEE) -> 403, nunca guarda")
    void cambiarEstadoComoTraineeEsRechazado() {
        var command = new CambiarEstadoCohorteCommand(trainee, CohorteId.newId(), EstadoCohorte.ACTIVA);
        assertThatThrownBy(() -> service.cambiarEstado(command)).isInstanceOf(NotAuthorizedException.class);
        verify(saveCohortePort, never()).save(any());
    }

    @Test
    @DisplayName("eliminar(): rol sin permiso (TRAINEE) -> 403, nunca borra")
    void eliminarComoTraineeEsRechazado() {
        var command = new EliminarCohorteCommand(trainee, CohorteId.newId());
        assertThatThrownBy(() -> service.eliminar(command)).isInstanceOf(NotAuthorizedException.class);
        verify(eliminarCohortePort, never()).eliminar(any());
    }

    @Test
    @DisplayName("listar(): rol sin permiso (TRAINEE) -> 403 — la rama de alcance es solo MENTOR/ADMIN")
    void listarComoTraineeEsRechazado() {
        assertThatThrownBy(() -> service.listar(trainee, null)).isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    @DisplayName("obtener(): rol sin permiso (TRAINEE) -> 403")
    void obtenerComoTraineeEsRechazado() {
        CohorteId id = CohorteId.newId();
        assertThatThrownBy(() -> service.obtener(trainee, id)).isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    @DisplayName("obtener(): un MENTOR no accede a una cohorte que no es la de su celula -> 403")
    void obtenerComoMentorDeOtraCohorteEsRechazado() {
        Celula propia = Celula.rehydrate(CelulaId.newId(), "Celula 1", mentor, CohorteId.newId(), null, null,
                CLOCK.now(), CLOCK.now());
        when(loadCelulaPort.porMentor(mentor)).thenReturn(Optional.of(propia));

        CohorteId ajena = CohorteId.newId();
        assertThatThrownBy(() -> service.obtener(mentor, ajena)).isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    @DisplayName("actualizar(): cuenta SUSPENDIDA -> 403 aunque el rol sea ADMIN")
    void actualizarConAdminSuspendidoFalla() {
        var command = new ActualizarCohorteCommand(adminSuspendido, CohorteId.newId(), "Otro", null, null, false);
        assertThatThrownBy(() -> service.actualizar(command)).isInstanceOf(NotAuthorizedException.class);
        verify(saveCohortePort, never()).save(any());
    }

    @Test
    @DisplayName("cambiarEstado(): cuenta SUSPENDIDA -> 403 aunque el rol sea ADMIN")
    void cambiarEstadoConAdminSuspendidoFalla() {
        var command = new CambiarEstadoCohorteCommand(adminSuspendido, CohorteId.newId(), EstadoCohorte.ACTIVA);
        assertThatThrownBy(() -> service.cambiarEstado(command)).isInstanceOf(NotAuthorizedException.class);
        verify(saveCohortePort, never()).save(any());
    }

    @Test
    @DisplayName("eliminar(): cuenta SUSPENDIDA -> 403 aunque el rol sea ADMIN")
    void eliminarConAdminSuspendidoFalla() {
        var command = new EliminarCohorteCommand(adminSuspendido, CohorteId.newId());
        assertThatThrownBy(() -> service.eliminar(command)).isInstanceOf(NotAuthorizedException.class);
        verify(eliminarCohortePort, never()).eliminar(any());
    }

    @Test
    @DisplayName("listar(): cuenta SUSPENDIDA -> 403 aunque el rol sea ADMIN")
    void listarConAdminSuspendidoFalla() {
        assertThatThrownBy(() -> service.listar(adminSuspendido, null)).isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    @DisplayName("obtener(): cuenta SUSPENDIDA -> 403 aunque el rol sea ADMIN")
    void obtenerConAdminSuspendidoFalla() {
        CohorteId id = CohorteId.newId();
        assertThatThrownBy(() -> service.obtener(adminSuspendido, id)).isInstanceOf(NotAuthorizedException.class);
    }
}
