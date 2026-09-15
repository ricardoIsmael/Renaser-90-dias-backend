package com.renaser.os.habits.application.services;

import com.renaser.os.community.api.AcompanamientoFinder;
import com.renaser.os.habits.application.ports.in.acompanamiento.ConsultaDeAcompanante;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.ProgresoParticipanteHabits;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.RolParticipante;
import com.renaser.os.habits.application.ports.out.habitosaprendiz.LeerHabitosPersonalizadosPort;
import com.renaser.os.habits.application.ports.out.preferencia.HistorialCambioHorarioPort;
import com.renaser.os.habits.application.ports.out.radar.LoadRegistroRadarPort;
import com.renaser.os.habits.domain.model.radar.RegistroRadar;
import com.renaser.os.habits.domain.model.radar.RegistroRadarId;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Lo que un acompañante puede leer de su alumno. Las pruebas que importan acá no son las del
 * camino feliz sino las dos negativas, y estan por duplicado (habitos y Codigo Renaser) a
 * proposito: son dos puertas distintas y cada una tiene que cerrar sola.
 *
 * <p>El reloj se fija a las 03:00 UTC —que en Lima son las 22:00 del dia anterior— por la regla
 * 02 §3: es la franja donde la fecha del servidor y la del participante no coinciden, y donde un
 * calculo de dia hecho con la fecha del proceso se delata.
 */
@ExtendWith(MockitoExtension.class)
class AcompanamientoDeAlumnoServiceTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-09-15T03:00:00Z"));
    private static final UUID GRUPO = UUID.randomUUID();

    @Mock
    private AcompanamientoFinder acompanamientoFinder;
    @Mock
    private HabitoAdminGuard guardDeAdmin;
    @Mock
    private ConsultarProgresoParticipanteHabitsPort progresoPort;
    @Mock
    private LeerHabitosPersonalizadosPort leerHabitosPort;
    @Mock
    private HistorialCambioHorarioPort historialPort;
    @Mock
    private LoadRegistroRadarPort loadRadarPort;

    private AcompanamientoDeAlumnoService service;
    private UserId mentor;
    private UserId alumno;

    @BeforeEach
    void setUp() {
        mentor = UserId.of(UUID.randomUUID());
        alumno = UserId.of(UUID.randomUUID());
        var habitosDeAprendiz = new HabitosDeAprendizAdminService(guardDeAdmin, progresoPort, leerHabitosPort,
                historialPort, CLOCK);
        service = new AcompanamientoDeAlumnoService(acompanamientoFinder, habitosDeAprendiz, loadRadarPort, CLOCK);
    }

    // ─── habitos del alumno ────────────────────────────────────────────────

    @Test
    void habitosDeAlumnoDevuelveLaVistaEnLaZonaDelALUMNO() {
        acompanaDeVerdad();
        when(progresoPort.deParticipante(alumno))
                .thenReturn(Optional.of(new ProgresoParticipanteHabits(12, "America/Lima", RolParticipante.TRAINEE,
                        false, true)));
        when(leerHabitosPort.deAprendiz(any(), anyInt(), any(), any())).thenReturn(List.of());
        when(historialPort.distintosHabitosCambiadosDesde(any(), any())).thenReturn(List.of());

        var vista = service.habitosDeAlumno(new ConsultaDeAcompanante(mentor, GRUPO, alumno));

        assertThat(vista.aprendizId()).isEqualTo(alumno);
        assertThat(vista.diaPrograma()).isEqualTo(12);
        assertThat(vista.zonaHoraria()).isEqualTo("America/Lima");
        // 03:00 UTC del 15 son las 22:00 del 14 en Lima: la vista es del dia del alumno, no del servidor.
        assertThat(vista.fechaLocal()).isEqualTo("2026-09-14");
    }

    @Test
    void habitosDeAlumnoRechazaAQuienNoAcompanaEseGrupo() {
        when(acompanamientoFinder.acompanaVigente(mentor, GRUPO, CLOCK.now())).thenReturn(false);

        assertThatThrownBy(() -> service.habitosDeAlumno(new ConsultaDeAcompanante(mentor, GRUPO, alumno)))
                .isInstanceOf(NotAuthorizedException.class);

        verifyNoInteractions(progresoPort, leerHabitosPort);
    }

    /**
     * La comprobacion que no es obvia: sin ella, un mentor legitimo lee a CUALQUIER participante
     * pasando el id de su propio grupo. Es el mismo agujero que cerro V12 en `mentoring`.
     */
    @Test
    void habitosDeAlumnoRechazaUnAlumnoQueNoEsDeEseGrupo() {
        when(acompanamientoFinder.acompanaVigente(mentor, GRUPO, CLOCK.now())).thenReturn(true);
        when(acompanamientoFinder.aprendicesVigentes(GRUPO, CLOCK.now()))
                .thenReturn(List.of(UserId.of(UUID.randomUUID())));

        assertThatThrownBy(() -> service.habitosDeAlumno(new ConsultaDeAcompanante(mentor, GRUPO, alumno)))
                .isInstanceOf(NotAuthorizedException.class);

        verifyNoInteractions(progresoPort, leerHabitosPort);
    }

    // ─── Codigo Renaser del alumno ─────────────────────────────────────────

    @Test
    void codigoRenaserDeAlumnoDevuelveLaPaginaSinCursorCuandoNoEstaLlena() {
        acompanaDeVerdad();
        when(loadRadarPort.historialDeParticipante(alumno, null, 84)).thenReturn(List.of(registro()));

        var pagina = service.codigoRenaserDeAlumno(new ConsultaDeAcompanante(mentor, GRUPO, alumno), null, 84);

        assertThat(pagina.entradas()).hasSize(1);
        assertThat(pagina.siguienteCursor()).isNull();
    }

    @Test
    void codigoRenaserDeAlumnoDevuelveCursorCuandoLaPaginaVineLlena() {
        acompanaDeVerdad();
        when(loadRadarPort.historialDeParticipante(alumno, null, 1)).thenReturn(List.of(registro()));

        var pagina = service.codigoRenaserDeAlumno(new ConsultaDeAcompanante(mentor, GRUPO, alumno), null, 1);

        assertThat(pagina.siguienteCursor()).isEqualTo(registro().creadoEn());
    }

    @Test
    void codigoRenaserDeAlumnoRechazaAQuienNoAcompanaEseGrupo() {
        when(acompanamientoFinder.acompanaVigente(mentor, GRUPO, CLOCK.now())).thenReturn(false);

        assertThatThrownBy(() -> service.codigoRenaserDeAlumno(new ConsultaDeAcompanante(mentor, GRUPO, alumno),
                null, 84)).isInstanceOf(NotAuthorizedException.class);

        verifyNoInteractions(loadRadarPort);
    }

    @Test
    void codigoRenaserDeAlumnoRechazaUnAlumnoQueNoEsDeEseGrupo() {
        when(acompanamientoFinder.acompanaVigente(mentor, GRUPO, CLOCK.now())).thenReturn(true);
        when(acompanamientoFinder.aprendicesVigentes(GRUPO, CLOCK.now()))
                .thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> service.codigoRenaserDeAlumno(new ConsultaDeAcompanante(mentor, GRUPO, alumno),
                null, 84)).isInstanceOf(NotAuthorizedException.class);

        verifyNoInteractions(loadRadarPort);
    }

    /** Nadie puede leer el Codigo Renaser por esta puerta sin pasar por el guard del grupo. */
    @Test
    void elGuardCorreAntesDeTocarLaBase() {
        when(acompanamientoFinder.acompanaVigente(mentor, GRUPO, CLOCK.now())).thenReturn(false);

        assertThatThrownBy(() -> service.codigoRenaserDeAlumno(new ConsultaDeAcompanante(mentor, GRUPO, alumno),
                null, 84)).isInstanceOf(NotAuthorizedException.class);

        verify(loadRadarPort, never()).historialDeParticipante(any(), any(), anyInt());
    }

    private void acompanaDeVerdad() {
        when(acompanamientoFinder.acompanaVigente(mentor, GRUPO, CLOCK.now())).thenReturn(true);
        when(acompanamientoFinder.aprendicesVigentes(GRUPO, CLOCK.now())).thenReturn(List.of(alumno));
    }

    private RegistroRadar registro() {
        return RegistroRadar.rehydrate(RegistroRadarId.of(UUID.fromString("00000000-0000-0000-0000-00000000000a")),
                alumno, "Escribiendo", "Que llego tarde", "Ansiedad", 6, "La llamada de ventas",
                Instant.parse("2026-09-14T13:00:00Z"));
    }
}
