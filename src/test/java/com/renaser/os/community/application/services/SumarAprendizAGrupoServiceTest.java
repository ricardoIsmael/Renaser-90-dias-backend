package com.renaser.os.community.application.services;

import com.renaser.os.community.api.ComposicionDeCelulaCambiadaEvent;
import com.renaser.os.community.application.ports.in.celula.ConsultarCelulasUseCase;
import com.renaser.os.community.application.ports.in.celula.SumarAprendizAGrupoUseCase.SumarAprendizAGrupoCommand;
import com.renaser.os.community.application.ports.out.acompanamiento.LoadAsignacionesPort;
import com.renaser.os.community.application.ports.out.acompanamiento.LoadPoliticaMentoriaPort;
import com.renaser.os.community.application.ports.out.acompanamiento.SaveAsignacionPort;
import com.renaser.os.community.application.ports.out.celula.LoadCelulaPort;
import com.renaser.os.community.application.ports.out.participante.ConsultarCelulaDeParticipantePort;
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
import java.util.ArrayList;
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
 * Lo que estas pruebas cuidan es una sola frase del dueño: <i>"un estudiante o aprendiz puede estar
 * en varios grupos múltiples a la vez"</i> (D-139). Es decir: sumar NO es trasladar. Si alguna vez
 * este servicio empieza a cerrar la pertenencia anterior, la primera prueba se pone roja.
 *
 * <p>Y cuidan lo segundo, que es donde estaba el riesgo real: que el alta adicional NO mueva
 * {@code participantes_programa.celula_id}. Esa columna nombra un solo grupo y siete lecturas del
 * producto dependen de ella.
 *
 * <p><b>Sobre el reloj del fixture</b> (.claude/rules/02 §3): acá se fija a las 15:00 UTC, una hora
 * que cae el mismo día calendario en Lima, y está bien que así sea — este servicio no deriva
 * ninguna fecha local. Solo usa {@code clock.now()} como instante de apertura del intervalo y para
 * preguntar por vigencia, que son comparaciones de instantes y no de días. No hay acá un caso
 * "entre 00:00 y 05:00 UTC" que pueda esconder nada.
 */
@ExtendWith(MockitoExtension.class)
class SumarAprendizAGrupoServiceTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-09-10T15:00:00Z"));

    @Mock
    private LoadCelulaPort loadCelulaPort;
    @Mock
    private LoadAsignacionesPort loadAsignacionesPort;
    @Mock
    private SaveAsignacionPort saveAsignacionPort;
    @Mock
    private LoadPoliticaMentoriaPort loadPoliticaMentoriaPort;
    @Mock
    private ConsultarCelulaDeParticipantePort consultarCelulaDeParticipantePort;
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

    private SumarAprendizAGrupoService service;

    private final UserId admin = UserId.of(UUID.randomUUID());
    private final UserId adminSuspendido = UserId.of(UUID.randomUUID());
    private final UserId mentor = UserId.of(UUID.randomUUID());
    private final UserId aprendiz = UserId.of(UUID.randomUUID());

    @BeforeEach
    void setUp() {
        service = new SumarAprendizAGrupoService(loadCelulaPort, loadAsignacionesPort, saveAsignacionPort,
                loadPoliticaMentoriaPort, consultarCelulaDeParticipantePort, userSummaryFinder,
                asignacionCelulaPort, consultarCelulas, eventos, CLOCK, idGenerator);
        lenient().when(idGenerator.newId()).thenAnswer(inv -> UUID.randomUUID());
        lenient().when(loadPoliticaMentoriaPort.porCohorte(any())).thenReturn(Optional.empty());
        lenient().when(loadAsignacionesPort.porCelula(any())).thenReturn(List.of());
        lenient().when(loadAsignacionesPort.porUsuario(any())).thenReturn(List.of());
        lenient().when(consultarCelulaDeParticipantePort.celulaDeUsuario(any())).thenReturn(Optional.empty());
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

    // ── Lo que define esta funcionalidad ────────────────────────────────────

    @Test
    @DisplayName("sumar NO cierra la pertenencia que ya tenia: queda en los dos grupos")
    void sumarNoCierraLaPertenenciaAnterior() {
        Celula primero = grupo();
        Celula segundo = grupo();
        AsignacionCelula enElPrimero = pertenenciaAbierta(primero.id(), aprendiz,
                FuncionAcompanamiento.APRENDIZ, "alta-manual|" + aprendiz.value() + "|" + primero.id().value());
        when(loadAsignacionesPort.porUsuario(aprendiz)).thenReturn(List.of(enElPrimero));

        service.sumar(new SumarAprendizAGrupoCommand(admin, segundo.id(), aprendiz));

        /* Esta es la prueba de la funcionalidad entera. El traslado —`ComposicionDeCelulaService`—
           deja esta asignacion cerrada; acá tiene que seguir viva. */
        assertThat(enElPrimero.vigente()).as("su grupo anterior NO se toca").isTrue();

        ArgumentCaptor<AsignacionCelula> guardada = ArgumentCaptor.forClass(AsignacionCelula.class);
        verify(saveAsignacionPort).save(guardada.capture());
        assertThat(guardada.getValue().celulaId()).isEqualTo(segundo.id());
        assertThat(guardada.getValue().funcion()).isEqualTo(FuncionAcompanamiento.APRENDIZ);
        assertThat(guardada.getValue().vigente()).isTrue();
    }

    @Test
    @DisplayName("sumar NO mueve el puntero: `celula_id` sigue nombrando al grupo principal")
    void sumarNoMueveElPunteroDelGrupoPrincipal() {
        Celula primero = grupo();
        Celula segundo = grupo();
        when(loadAsignacionesPort.porUsuario(aprendiz)).thenReturn(List.of(pertenenciaAbierta(
                primero.id(), aprendiz, FuncionAcompanamiento.APRENDIZ, "alta-manual")));
        when(consultarCelulaDeParticipantePort.celulaDeUsuario(aprendiz)).thenReturn(Optional.of(primero.id()));

        service.sumar(new SumarAprendizAGrupoCommand(admin, segundo.id(), aprendiz));

        /* `participantes_programa.celula_id` es UNA columna y siete lecturas dependen de ella
           ("mi grupo", "mis compañeros", conteo del panel, ranking, ficha, calendario). Moverla
           le cambiaria de grupo la pantalla principal a alguien que no pidio nada. */
        verify(asignacionCelulaPort, never()).sincronizarAcompanamiento(any(), any(), any());
    }

    @Test
    @DisplayName("sin grupo previo el puntero SI se estrena: el primero que se suma es el principal")
    void sumarEstrenaElPunteroCuandoEstaVacio() {
        Celula destino = grupo();
        when(loadAsignacionesPort.porCelula(destino.id())).thenReturn(List.of(
                pertenenciaAbierta(destino.id(), mentor, FuncionAcompanamiento.MENTOR, "mentor")));

        service.sumar(new SumarAprendizAGrupoCommand(admin, destino.id(), aprendiz));

        // Con el puntero en null, la app le diria "todavia no tienes grupo" a alguien que si lo
        // tiene en el historial. Y apunta al mentor vigente del grupo, no a null.
        verify(asignacionCelulaPort).sincronizarAcompanamiento(aprendiz, destino.id().value(), mentor);
    }

    @Test
    @DisplayName("sumarlo al grupo en el que YA esta no abre una segunda membresia")
    void sumarAlMismoGrupoEsIdempotente() {
        Celula destino = grupo();
        when(loadAsignacionesPort.porUsuario(aprendiz)).thenReturn(List.of(pertenenciaAbierta(
                destino.id(), aprendiz, FuncionAcompanamiento.APRENDIZ, "recepcion-alta|" + aprendiz.value())));

        service.sumar(new SumarAprendizAGrupoCommand(admin, destino.id(), aprendiz));

        // Dos clics del administrador no son dos membresias. Y no importa por que puerta entro la
        // primera: se mira la pertenencia vigente, no la clave con la que se creo.
        verify(saveAsignacionPort, never()).save(any());
        verify(eventos, never()).publishEvent(any());
    }

    @Test
    @DisplayName("la clave de operacion cuenta las entradas previas: volver a entrar no choca contra su propia fila cerrada")
    void laClaveDistingueUnaReincorporacion() {
        Celula destino = grupo();
        AsignacionCelula yaCerrada = pertenenciaAbierta(destino.id(), aprendiz, FuncionAcompanamiento.APRENDIZ,
                "suma-a-grupo|" + aprendiz.value() + "|" + destino.id().value() + "|0");
        yaCerrada.cerrar(CLOCK.now().minusSeconds(3_600), MotivoAsignacion.ADMINISTRATIVO);
        when(loadAsignacionesPort.porUsuario(aprendiz)).thenReturn(List.of(yaCerrada));

        service.sumar(new SumarAprendizAGrupoCommand(admin, destino.id(), aprendiz));

        ArgumentCaptor<AsignacionCelula> guardada = ArgumentCaptor.forClass(AsignacionCelula.class);
        verify(saveAsignacionPort).save(guardada.capture());
        /* Con una clave fija —solo persona y grupo— este INSERT moriria contra
           `asignaciones_celula_operacion_uk`, y a quien se suma, se retira y se vuelve a sumar al
           mismo grupo no se lo podria reincorporar nunca mas. */
        assertThat(guardada.getValue().claveOperacion()).endsWith("|1");
    }

    @Test
    @DisplayName("sumar avisa al chat del grupo destino, y de ninguno mas")
    void sumarAvisaSoloAlDestino() {
        Celula primero = grupo();
        Celula segundo = grupo();
        when(loadAsignacionesPort.porUsuario(aprendiz)).thenReturn(List.of(pertenenciaAbierta(
                primero.id(), aprendiz, FuncionAcompanamiento.APRENDIZ, "alta-manual")));

        service.sumar(new SumarAprendizAGrupoCommand(admin, segundo.id(), aprendiz));

        // A diferencia del traslado, no hay grupo de origen que quede sin este integrante.
        verify(eventos).publishEvent(new ComposicionDeCelulaCambiadaEvent(segundo.id().value(), CLOCK.now()));
        verify(eventos, never()).publishEvent(new ComposicionDeCelulaCambiadaEvent(primero.id().value(), CLOCK.now()));
    }

    // ── Lo que NO cambia respecto del alta por traslado ─────────────────────

    @Test
    @DisplayName("el cupo se respeta igual: sumar a un grupo lleno se rechaza")
    void sumarAGrupoLlenoSeRechaza() {
        Celula destino = grupo();
        List<AsignacionCelula> llenos = new ArrayList<>();
        for (int i = 0; i < PoliticaMentoria.porDefecto(destino.cohorteId()).capacidadCelula(); i++) {
            llenos.add(pertenenciaAbierta(destino.id(), UserId.of(UUID.randomUUID()),
                    FuncionAcompanamiento.APRENDIZ, "ocupado-" + i));
        }
        when(loadAsignacionesPort.porCelula(destino.id())).thenReturn(llenos);

        assertThatThrownBy(() -> service.sumar(new SumarAprendizAGrupoCommand(admin, destino.id(), aprendiz)))
                .isInstanceOf(AsignacionInvalidaException.class)
                .hasMessageContaining("cupo");
        verify(saveAsignacionPort, never()).save(any());
    }

    @Test
    @DisplayName("un MENTOR no puede sumar a nadie -> 403 y no escribe nada")
    void sumarComoMentorEsRechazado() {
        Celula destino = grupo();

        assertThatThrownBy(() -> service.sumar(new SumarAprendizAGrupoCommand(mentor, destino.id(), aprendiz)))
                .isInstanceOf(NotAuthorizedException.class);
        verify(saveAsignacionPort, never()).save(any());
    }

    @Test
    @DisplayName("cuenta SUSPENDIDA -> 403 aunque el rol sea ADMIN")
    void sumarConAdminSuspendidoEsRechazado() {
        assertThatThrownBy(() -> service.sumar(new SumarAprendizAGrupoCommand(adminSuspendido,
                CelulaId.of(UUID.randomUUID()), aprendiz)))
                .isInstanceOf(NotAuthorizedException.class);
        verify(saveAsignacionPort, never()).save(any());
    }

    @Test
    @DisplayName("a un grupo se suman aprendices: un MENTOR como alumno se rechaza")
    void sumarAUnMentorComoAlumnoEsRechazado() {
        Celula destino = grupo();

        assertThatThrownBy(() -> service.sumar(new SumarAprendizAGrupoCommand(admin, destino.id(), mentor)))
                .isInstanceOf(IllegalArgumentException.class);
        verify(saveAsignacionPort, never()).save(any());
    }
}
