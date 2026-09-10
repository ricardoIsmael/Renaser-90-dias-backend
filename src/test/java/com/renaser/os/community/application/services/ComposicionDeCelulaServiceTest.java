package com.renaser.os.community.application.services;

import com.renaser.os.community.api.ComposicionDeCelulaCambiadaEvent;
import com.renaser.os.community.application.ports.in.celula.AsignarAprendizCelulaUseCase.AsignarAprendizCelulaCommand;
import com.renaser.os.community.application.ports.in.celula.AsignarMentorCelulaUseCase.AsignarMentorCelulaCommand;
import com.renaser.os.community.application.ports.in.celula.ConsultarCelulasUseCase;
import com.renaser.os.community.application.ports.in.celula.ConsultarCelulasUseCase.CelulaDetalle;
import com.renaser.os.community.application.ports.in.celula.QuitarAprendizCelulaUseCase.QuitarAprendizCelulaCommand;
import com.renaser.os.community.application.ports.in.celula.QuitarMentorCelulaUseCase.QuitarMentorCelulaCommand;
import com.renaser.os.community.application.ports.out.acompanamiento.LoadAsignacionesPort;
import com.renaser.os.community.application.ports.out.acompanamiento.LoadPoliticaMentoriaPort;
import com.renaser.os.community.application.ports.out.acompanamiento.SaveAsignacionPort;
import com.renaser.os.community.application.ports.out.celula.ExistePerfilMentorPort;
import com.renaser.os.community.application.ports.out.celula.LoadCelulaPort;
import com.renaser.os.community.application.ports.out.celula.SaveCelulaPort;
import com.renaser.os.community.domain.model.acompanamiento.AsignacionCelula;
import com.renaser.os.community.domain.model.acompanamiento.AsignacionId;
import com.renaser.os.community.domain.model.acompanamiento.AsignacionInvalidaException;
import com.renaser.os.community.domain.model.acompanamiento.FuncionAcompanamiento;
import com.renaser.os.community.domain.model.acompanamiento.MotivoAsignacion;
import com.renaser.os.community.domain.model.acompanamiento.PoliticaMentoria;
import com.renaser.os.community.domain.model.celula.Celula;
import com.renaser.os.community.domain.model.celula.CelulaId;
import com.renaser.os.community.domain.model.cohorte.CohorteId;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.AsignacionCelulaPort;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Lo que estas pruebas cuidan es una sola cosa: que agregar o sacar a alguien de un grupo NO sea
 * escribir un puntero. Antes lo era, y el efecto se veia lejos del panel — el chat no se enteraba,
 * el seguimiento semanal daba 403 y el cupo no contaba a nadie.
 */
@ExtendWith(MockitoExtension.class)
class ComposicionDeCelulaServiceTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-09-10T15:00:00Z"));

    @Mock
    private LoadCelulaPort loadCelulaPort;
    @Mock
    private SaveCelulaPort saveCelulaPort;
    @Mock
    private LoadAsignacionesPort loadAsignacionesPort;
    @Mock
    private SaveAsignacionPort saveAsignacionPort;
    @Mock
    private LoadPoliticaMentoriaPort loadPoliticaMentoriaPort;
    @Mock
    private ExistePerfilMentorPort existePerfilMentorPort;
    @Mock
    private UserSummaryFinder userSummaryFinder;
    @Mock
    private AsignacionCelulaPort asignacionCelulaPort;
    @Mock
    private ConsultarCelulasUseCase consultarCelulas;
    @Mock
    private ApplicationEventPublisher eventos;
    @Mock
    private IdGenerator idGenerator;

    private ComposicionDeCelulaService service;

    private final UserId admin = UserId.of(UUID.randomUUID());
    private final UserId adminSuspendido = UserId.of(UUID.randomUUID());
    private final UserId mentor = UserId.of(UUID.randomUUID());
    private final UserId aprendiz = UserId.of(UUID.randomUUID());

    @BeforeEach
    void setUp() {
        service = new ComposicionDeCelulaService(loadCelulaPort, saveCelulaPort, loadAsignacionesPort,
                saveAsignacionPort, loadPoliticaMentoriaPort, existePerfilMentorPort, userSummaryFinder,
                asignacionCelulaPort, consultarCelulas, eventos, CLOCK, idGenerator);
        lenient().when(idGenerator.newId()).thenAnswer(inv -> UUID.randomUUID());
        lenient().when(loadPoliticaMentoriaPort.porCohorte(any())).thenReturn(Optional.empty());
        lenient().when(loadAsignacionesPort.porCelula(any())).thenReturn(List.of());
        lenient().when(loadAsignacionesPort.porUsuario(any())).thenReturn(List.of());
        lenient().when(consultarCelulas.obtener(any(), any())).thenReturn(null);
        usuario(admin, UserRole.ADMIN, UserStatus.ACTIVE);
        usuario(adminSuspendido, UserRole.ADMIN, UserStatus.SUSPENDED);
        usuario(mentor, UserRole.MENTOR, UserStatus.ACTIVE);
        usuario(aprendiz, UserRole.TRAINEE, UserStatus.ACTIVE);
    }

    private void usuario(UserId id, UserRole rol, UserStatus estado) {
        lenient().when(userSummaryFinder.findById(id))
                .thenReturn(Optional.of(new UserSummary(id, rol.name(), null, rol, estado)));
    }

    private Celula grupo() {
        Celula celula = Celula.rehydrate(CelulaId.of(UUID.randomUUID()), "Fenix", null,
                CohorteId.of(UUID.randomUUID()), null, null, CLOCK.now(), CLOCK.now());
        lenient().when(loadCelulaPort.porId(celula.id())).thenReturn(Optional.of(celula));
        return celula;
    }

    private AsignacionCelula pertenenciaAbierta(CelulaId celulaId, UserId usuarioId,
                                                 FuncionAcompanamiento funcion, String clave) {
        return AsignacionCelula.abrir(AsignacionId.of(UUID.randomUUID()), celulaId, usuarioId, funcion,
                CLOCK.now().minusSeconds(86_400), MotivoAsignacion.ADMINISTRATIVO, admin, clave);
    }

    // ── Alta de aprendiz ────────────────────────────────────────────────────

    @Test
    @DisplayName("asignar(aprendiz): abre el intervalo, sincroniza el puntero y avisa al chat")
    void altaEscribeHistorialPunteroYEvento() {
        Celula destino = grupo();

        service.asignar(new AsignarAprendizCelulaCommand(admin, destino.id(), aprendiz));

        ArgumentCaptor<AsignacionCelula> guardada = ArgumentCaptor.forClass(AsignacionCelula.class);
        verify(saveAsignacionPort).save(guardada.capture());
        assertThat(guardada.getValue().funcion()).isEqualTo(FuncionAcompanamiento.APRENDIZ);
        assertThat(guardada.getValue().celulaId()).isEqualTo(destino.id());
        assertThat(guardada.getValue().vigente()).isTrue();
        verify(asignacionCelulaPort).sincronizarAcompanamiento(aprendiz, destino.id().value(), null);
        verify(eventos).publishEvent(new ComposicionDeCelulaCambiadaEvent(destino.id().value(), CLOCK.now()));
    }

    @Test
    @DisplayName("asignar(aprendiz): repetir el mismo comando NO abre una segunda membresia")
    void altaRepetidaEsIdempotente() {
        Celula destino = grupo();
        String clave = "alta-manual|" + aprendiz.value() + "|" + destino.id().value();
        when(loadAsignacionesPort.porUsuario(aprendiz))
                .thenReturn(List.of(pertenenciaAbierta(destino.id(), aprendiz, FuncionAcompanamiento.APRENDIZ, clave)));

        service.asignar(new AsignarAprendizCelulaCommand(admin, destino.id(), aprendiz));

        verify(saveAsignacionPort, never()).save(any());
        verify(eventos, never()).publishEvent(any());
    }

    @Test
    @DisplayName("asignar(aprendiz): mover a otro grupo cierra el anterior y avisa a LOS DOS chats")
    void altaEnOtroGrupoCierraElAnteriorYAvisaAAmbos() {
        Celula origen = grupo();
        Celula destino = grupo();
        AsignacionCelula anterior = pertenenciaAbierta(origen.id(), aprendiz, FuncionAcompanamiento.APRENDIZ,
                "alta-manual|" + aprendiz.value() + "|" + origen.id().value());
        when(loadAsignacionesPort.porUsuario(aprendiz)).thenReturn(List.of(anterior));

        service.asignar(new AsignarAprendizCelulaCommand(admin, destino.id(), aprendiz));

        assertThat(anterior.vigente()).isFalse();
        verify(eventos).publishEvent(new ComposicionDeCelulaCambiadaEvent(destino.id().value(), CLOCK.now()));
        verify(eventos).publishEvent(new ComposicionDeCelulaCambiadaEvent(origen.id().value(), CLOCK.now()));
    }

    @Test
    @DisplayName("asignar(aprendiz): grupo lleno -> se rechaza antes de tocar la base")
    void altaSinCupoSeRechaza() {
        Celula destino = grupo();
        List<AsignacionCelula> llenos = new java.util.ArrayList<>();
        for (int i = 0; i < PoliticaMentoria.porDefecto(destino.cohorteId()).capacidadCelula(); i++) {
            llenos.add(pertenenciaAbierta(destino.id(), UserId.of(UUID.randomUUID()),
                    FuncionAcompanamiento.APRENDIZ, "ocupado-" + i));
        }
        when(loadAsignacionesPort.porCelula(destino.id())).thenReturn(llenos);

        assertThatThrownBy(() -> service.asignar(new AsignarAprendizCelulaCommand(admin, destino.id(), aprendiz)))
                .isInstanceOf(AsignacionInvalidaException.class)
                .hasMessageContaining("cupo");
        verify(saveAsignacionPort, never()).save(any());
    }

    @Test
    @DisplayName("asignar(aprendiz): el mentor del grupo NO consume cupo")
    void elMentorNoOcupaLugar() {
        Celula destino = grupo();
        List<AsignacionCelula> composicion = new java.util.ArrayList<>();
        composicion.add(pertenenciaAbierta(destino.id(), mentor, FuncionAcompanamiento.MENTOR, "mentor"));
        for (int i = 0; i < PoliticaMentoria.porDefecto(destino.cohorteId()).capacidadCelula() - 1; i++) {
            composicion.add(pertenenciaAbierta(destino.id(), UserId.of(UUID.randomUUID()),
                    FuncionAcompanamiento.APRENDIZ, "ocupado-" + i));
        }
        when(loadAsignacionesPort.porCelula(destino.id())).thenReturn(composicion);

        service.asignar(new AsignarAprendizCelulaCommand(admin, destino.id(), aprendiz));

        verify(saveAsignacionPort).save(any());
        // Y el puntero del aprendiz apunta al mentor vigente del grupo, no a null.
        verify(asignacionCelulaPort).sincronizarAcompanamiento(aprendiz, destino.id().value(), mentor);
    }

    @Test
    @DisplayName("asignar(aprendiz): un MENTOR no puede -> 403 y no escribe nada")
    void altaComoMentorEsRechazada() {
        Celula destino = grupo();
        assertThatThrownBy(() -> service.asignar(new AsignarAprendizCelulaCommand(mentor, destino.id(), aprendiz)))
                .isInstanceOf(NotAuthorizedException.class);
        verify(saveAsignacionPort, never()).save(any());
    }

    @Test
    @DisplayName("asignar(aprendiz): cuenta SUSPENDIDA -> 403 aunque el rol sea ADMIN")
    void altaConAdminSuspendidoEsRechazada() {
        assertThatThrownBy(() -> service.asignar(new AsignarAprendizCelulaCommand(adminSuspendido,
                CelulaId.of(UUID.randomUUID()), aprendiz)))
                .isInstanceOf(NotAuthorizedException.class);
        verify(saveAsignacionPort, never()).save(any());
    }

    @Test
    @DisplayName("asignar(aprendiz): un MENTOR como aprendiz -> rechazo")
    void unMentorNoEsAprendiz() {
        Celula destino = grupo();
        assertThatThrownBy(() -> service.asignar(new AsignarAprendizCelulaCommand(admin, destino.id(), mentor)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── Baja de aprendiz ────────────────────────────────────────────────────

    @Test
    @DisplayName("quitar(aprendiz): cierra SU intervalo en ESE grupo y limpia el puntero")
    void bajaCierraElIntervaloDelGrupoIndicado() {
        Celula suGrupo = grupo();
        AsignacionCelula suya = pertenenciaAbierta(suGrupo.id(), aprendiz, FuncionAcompanamiento.APRENDIZ, "alta");
        when(loadAsignacionesPort.porUsuario(aprendiz)).thenReturn(List.of(suya));

        service.quitar(new QuitarAprendizCelulaCommand(admin, suGrupo.id(), aprendiz));

        assertThat(suya.vigente()).isFalse();
        verify(saveAsignacionPort).save(suya);
        verify(asignacionCelulaPort).quitarCelula(admin, aprendiz);
        verify(eventos).publishEvent(new ComposicionDeCelulaCambiadaEvent(suGrupo.id().value(), CLOCK.now()));
    }

    @Test
    @DisplayName("quitar(aprendiz) desde el grupo EQUIVOCADO: rechazo, y su pertenencia real sigue viva")
    void bajaDesdeOtroGrupoNoLoSacaDelSuyo() {
        Celula suGrupo = grupo();
        Celula otroGrupo = grupo();
        AsignacionCelula suya = pertenenciaAbierta(suGrupo.id(), aprendiz, FuncionAcompanamiento.APRENDIZ, "alta");
        when(loadAsignacionesPort.porUsuario(aprendiz)).thenReturn(List.of(suya));

        assertThatThrownBy(() -> service.quitar(new QuitarAprendizCelulaCommand(admin, otroGrupo.id(), aprendiz)))
                .isInstanceOf(AsignacionInvalidaException.class);

        assertThat(suya.vigente()).isTrue();
        verify(saveAsignacionPort, never()).save(any());
        verify(asignacionCelulaPort, never()).quitarCelula(any(), any());
    }

    // ── Mentor ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("asignar(mentor): abre intervalo, mueve el puntero del grupo y el de sus aprendices")
    void altaDeMentorEscribeHistorialYPunteros() {
        Celula destino = grupo();
        when(existePerfilMentorPort.existe(mentor)).thenReturn(true);
        when(saveCelulaPort.save(any())).thenAnswer(inv -> inv.getArgument(0));
        AsignacionCelula alumno = pertenenciaAbierta(destino.id(), aprendiz, FuncionAcompanamiento.APRENDIZ, "alta");
        when(loadAsignacionesPort.porCelula(destino.id())).thenReturn(List.of(alumno));

        service.asignar(new AsignarMentorCelulaCommand(admin, destino.id(), mentor));

        ArgumentCaptor<AsignacionCelula> guardada = ArgumentCaptor.forClass(AsignacionCelula.class);
        verify(saveAsignacionPort).save(guardada.capture());
        assertThat(guardada.getValue().funcion()).isEqualTo(FuncionAcompanamiento.MENTOR);
        assertThat(destino.mentorId()).isEqualTo(mentor);
        verify(eventos).publishEvent(new ComposicionDeCelulaCambiadaEvent(destino.id().value(), CLOCK.now()));
    }

    @Test
    @DisplayName("asignar(mentor): el saliente pierde el intervalo, no solo el puntero")
    void cambioDeMentorCierraAlSaliente() {
        Celula destino = grupo();
        UserId saliente = UserId.of(UUID.randomUUID());
        usuario(saliente, UserRole.MENTOR, UserStatus.ACTIVE);
        AsignacionCelula delSaliente = pertenenciaAbierta(destino.id(), saliente, FuncionAcompanamiento.MENTOR,
                "mentor-viejo");
        when(loadAsignacionesPort.porCelula(destino.id())).thenReturn(List.of(delSaliente));
        when(existePerfilMentorPort.existe(mentor)).thenReturn(true);
        when(saveCelulaPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.asignar(new AsignarMentorCelulaCommand(admin, destino.id(), mentor));

        assertThat(delSaliente.vigente()).isFalse();
        verify(saveAsignacionPort).save(delSaliente);
    }

    @Test
    @DisplayName("asignar(mentor): sin perfil_mentor -> se rechaza sin tocar el historial")
    void mentorSinPerfilEsRechazado() {
        Celula destino = grupo();
        when(existePerfilMentorPort.existe(mentor)).thenReturn(false);

        assertThatThrownBy(() -> service.asignar(new AsignarMentorCelulaCommand(admin, destino.id(), mentor)))
                .isInstanceOf(IllegalStateException.class);
        verify(saveAsignacionPort, never()).save(any());
    }

    @Test
    @DisplayName("quitar(mentor): cierra el intervalo y deja el puntero de los aprendices en null")
    void bajaDeMentorLimpiaPunteros() {
        Celula destino = grupo();
        AsignacionCelula delMentor = pertenenciaAbierta(destino.id(), mentor, FuncionAcompanamiento.MENTOR, "m");
        AsignacionCelula delAlumno = pertenenciaAbierta(destino.id(), aprendiz, FuncionAcompanamiento.APRENDIZ, "a");
        when(loadAsignacionesPort.porCelula(destino.id())).thenReturn(List.of(delMentor, delAlumno));
        when(saveCelulaPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.quitar(new QuitarMentorCelulaCommand(admin, destino.id()));

        assertThat(delMentor.vigente()).isFalse();
        assertThat(destino.mentorId()).isNull();
        verify(asignacionCelulaPort).sincronizarAcompanamiento(aprendiz, destino.id().value(), null);
    }

    @Test
    @DisplayName("mentor: un MENTOR no administra la composicion -> 403")
    void composicionDeMentorComoMentorEsRechazada() {
        assertThatThrownBy(() -> service.quitar(new QuitarMentorCelulaCommand(mentor, CelulaId.of(UUID.randomUUID()))))
                .isInstanceOf(NotAuthorizedException.class);
        verify(saveAsignacionPort, never()).save(any());
    }
}
