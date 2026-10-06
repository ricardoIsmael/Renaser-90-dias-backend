package com.renaser.os.calendar.application.services;

import com.renaser.os.calendar.application.ports.in.asistencia.PasarListaUseCase.MarcarAsistenciaCommand;
import com.renaser.os.calendar.application.ports.out.asistencia.LoadListaDeAsistenciaPort;
import com.renaser.os.calendar.application.ports.out.asistencia.SaveListaDeAsistenciaPort;
import com.renaser.os.calendar.application.ports.out.celula.ConsultarMiembrosCelulaPort;
import com.renaser.os.calendar.application.ports.out.confirmacion.HistorialDeRespuestasPort;
import com.renaser.os.calendar.application.ports.out.confirmacion.LoadConfirmacionPort;
import com.renaser.os.calendar.application.ports.out.curso.ResolverAudienciaCursoPort;
import com.renaser.os.calendar.application.ports.out.evento.LoadEventoPort;
import com.renaser.os.calendar.application.ports.out.evento.LoadExcepcionPort;
import com.renaser.os.calendar.application.ports.out.nivelmembresia.LoadNivelMembresiaPort;
import com.renaser.os.calendar.application.ports.out.participante.ConsultarProgresoParticipanteCalendarPort;
import com.renaser.os.calendar.application.ports.out.participante.ConsultarProgresoParticipanteCalendarPort.ProgresoParticipanteCalendar;
import com.renaser.os.calendar.application.ports.out.participante.ResolverAudienciaMasivaPort;
import com.renaser.os.calendar.application.ports.out.persona.ConsultarPersonasPort;
import com.renaser.os.calendar.application.ports.out.persona.ConsultarPersonasPort.Persona;
import com.renaser.os.calendar.domain.model.asistencia.CierreDeLista;
import com.renaser.os.calendar.domain.model.asistencia.EstadoAsistencia;
import com.renaser.os.calendar.domain.model.asistencia.MarcaDeAsistencia;
import com.renaser.os.calendar.domain.model.evento.Evento;
import com.renaser.os.calendar.domain.model.evento.EventoId;
import com.renaser.os.calendar.domain.model.evento.RolUsuario;
import com.renaser.os.calendar.domain.model.evento.TipoAudiencia;
import com.renaser.os.calendar.domain.model.evento.TipoEvento;
import com.renaser.os.calendar.domain.model.evento.TipoUbicacion;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Las reglas de «pasar lista» (D-256) sin base de datos. El evento es un lunes a las 20:00 de Lima (01:00 UTC
 * del martes) y el reloj cae en la madrugada UTC, cuando en Lima todavía es el lunes (regla 02 §3).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ListaDeAsistenciaServiceTest {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final Instant INICIO = Instant.parse("2026-10-06T01:00:00Z");
    /** 19:45 del lunes en Lima: dentro de la ventana. */
    private static final Instant ANTES_DE_EMPEZAR = Instant.parse("2026-10-06T00:45:00Z");

    @Mock private ConsultarProgresoParticipanteCalendarPort progresoPort;
    @Mock private LoadNivelMembresiaPort nivelPort;
    @Mock private LoadEventoPort loadEventoPort;
    @Mock private LoadExcepcionPort loadExcepcionPort;
    @Mock private ResolverAudienciaMasivaPort audienciaMasivaPort;
    @Mock private ConsultarMiembrosCelulaPort celulaPort;
    @Mock private ResolverAudienciaCursoPort cursoPort;
    @Mock private LoadConfirmacionPort confirmacionPort;
    @Mock private HistorialDeRespuestasPort historialPort;
    @Mock private LoadListaDeAsistenciaPort loadLista;
    @Mock private SaveListaDeAsistenciaPort saveLista;
    @Mock private ConsultarPersonasPort personasPort;

    private final UserId admin = UserId.of(UUID.randomUUID());
    private final UserId mentorCreador = UserId.of(UUID.randomUUID());
    private final UserId mentorAjeno = UserId.of(UUID.randomUUID());
    private final UserId aprendiz = UserId.of(UUID.randomUUID());
    private final UserId ajeno = UserId.of(UUID.randomUUID());
    private final UUID grupo = UUID.randomUUID();
    private final EventoId eventoId = EventoId.of(UUID.randomUUID());

    @BeforeEach
    void preparar() {
        rol(admin, RolUsuario.ADMIN);
        rol(mentorCreador, RolUsuario.MENTOR);
        rol(mentorAjeno, RolUsuario.MENTOR);
        rol(aprendiz, RolUsuario.TRAINEE);
        Evento evento = Evento.crear(eventoId, "Mentoria", null, INICIO, 60, LIMA, TipoUbicacion.MEET,
                "https://meet.google.com/abc", TipoAudiencia.CELULA, null, null, grupo, TipoEvento.ESPONTANEO, false,
                false, false, null, Set.of(), List.of(), mentorCreador, FixedClock.at(INICIO.minusSeconds(86_400)));
        when(loadEventoPort.byId(eventoId)).thenReturn(Optional.of(evento));
        when(loadExcepcionPort.porEvento(eventoId)).thenReturn(List.of());
        when(celulaPort.miembrosActivos(grupo)).thenReturn(List.of(aprendiz));
        when(personasPort.porIds(anyCollection())).thenReturn(Map.of(aprendiz, new Persona(aprendiz, "Ana Ríos", null)));
        when(loadLista.marcaDe(any(), any(), any())).thenReturn(Optional.empty());
        when(loadLista.cierre(any(), any())).thenReturn(Optional.empty());
        when(confirmacionPort.estadoDe(any(), any(), any())).thenReturn(Optional.empty());
    }

    @Test
    @DisplayName("el mentor que no creó el evento recibe 403; el que lo creó marca")
    void soloElMentorCreador() {
        assertThatThrownBy(() -> servicio(ANTES_DE_EMPEZAR).marcar(marcar(mentorAjeno, EstadoAsistencia.A_TIEMPO)))
                .isInstanceOf(NotAuthorizedException.class);

        servicio(ANTES_DE_EMPEZAR).marcar(marcar(mentorCreador, EstadoAsistencia.A_TIEMPO));

        verify(saveLista).guardar(any());
    }

    @Test
    @DisplayName("el aprendiz no ve la lista (ni la suya)")
    void elAprendizNoVe() {
        assertThatThrownBy(() -> servicio(ANTES_DE_EMPEZAR).ver(aprendiz, eventoId, INICIO))
                .isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    @DisplayName("con el reloj a las 00:45 UTC (19:45 del día anterior en Lima) ya se marca; a las 00:29 UTC todavía no")
    void ventanaConElRelojEnLaMadrugadaUtc() {
        assertThatThrownBy(() -> servicio(Instant.parse("2026-10-06T00:29:00Z"))
                .marcar(marcar(admin, EstadoAsistencia.A_TIEMPO)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("30 min antes");

        servicio(ANTES_DE_EMPEZAR).marcar(marcar(admin, EstadoAsistencia.A_TIEMPO));

        verify(saveLista).guardar(any());
    }

    @Test
    @DisplayName("pasadas las 12 h del fin ya no se marca ni se reabre")
    void despuesDelPlazoNada() {
        Instant tarde = Instant.parse("2026-10-06T14:01:00Z");

        assertThatThrownBy(() -> servicio(tarde).marcar(marcar(admin, EstadoAsistencia.TARDE)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("12 h");
        assertThatThrownBy(() -> servicio(tarde).reabrir(admin, eventoId, INICIO))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("con la lista cerrada, marcar es 409 hasta que se reabra")
    void listaCerradaNoSeMarca() {
        when(loadLista.cierre(eventoId, INICIO)).thenReturn(Optional.of(new CierreDeLista(ANTES_DE_EMPEZAR, admin)));

        assertThatThrownBy(() -> servicio(ANTES_DE_EMPEZAR).marcar(marcar(admin, EstadoAsistencia.A_TIEMPO)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("cerrada");
        verify(saveLista, never()).guardar(any());
    }

    @Test
    @DisplayName("marcar dos veces lo mismo no escribe la segunda vez (idempotente)")
    void mismoEstadoNoReescribe() {
        when(loadLista.marcaDe(eventoId, INICIO, aprendiz)).thenReturn(Optional.of(new MarcaDeAsistencia(eventoId,
                INICIO, aprendiz, EstadoAsistencia.A_TIEMPO, admin, ANTES_DE_EMPEZAR.minusSeconds(60))));

        servicio(ANTES_DE_EMPEZAR).marcar(marcar(admin, EstadoAsistencia.A_TIEMPO));

        verify(saveLista, never()).guardar(any());
    }

    @Test
    @DisplayName("estado null quita la marca (ausente)")
    void nullQuitaLaMarca() {
        servicio(ANTES_DE_EMPEZAR).marcar(marcar(admin, null));

        verify(saveLista).quitar(eventoId, INICIO, aprendiz);
    }

    @Test
    @DisplayName("a quien no está en la audiencia ni respondió no se lo marca (400)")
    void fueraDeLaListaEs400() {
        var comando = new MarcarAsistenciaCommand(admin, eventoId, INICIO, ajeno, EstadoAsistencia.A_TIEMPO);

        assertThatThrownBy(() -> servicio(ANTES_DE_EMPEZAR).marcar(comando))
                .isInstanceOf(IllegalArgumentException.class);
        verify(saveLista, never()).guardar(any());
    }

    @Test
    @DisplayName("cerrar una lista ya cerrada no la vuelve a cerrar (conserva quién y cuándo)")
    void cerrarDosVecesEsIdempotente() {
        when(loadLista.cierre(eventoId, INICIO)).thenReturn(Optional.of(new CierreDeLista(ANTES_DE_EMPEZAR, admin)));

        var lista = servicio(ANTES_DE_EMPEZAR.plusSeconds(3600)).cerrar(admin, eventoId, INICIO);

        verify(saveLista, never()).cerrar(any(), any(), any());
        assertThat(lista.cierre().cerradaEn()).isEqualTo(ANTES_DE_EMPEZAR);
        assertThat(lista.abiertaAhora()).isFalse();
    }

    @Test
    @DisplayName("un occurrenceStart que no es una fecha del evento es 400")
    void ocurrenciaInventada() {
        assertThatThrownBy(() -> servicio(ANTES_DE_EMPEZAR).ver(admin, eventoId, INICIO.plusSeconds(86_400)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private MarcarAsistenciaCommand marcar(UserId actor, EstadoAsistencia estado) {
        return new MarcarAsistenciaCommand(actor, eventoId, INICIO, aprendiz, estado);
    }

    private void rol(UserId quien, RolUsuario rol) {
        when(progresoPort.deParticipante(quien)).thenReturn(Optional.of(
                new ProgresoParticipanteCalendar(10, LIMA, rol, false, null)));
    }

    private ListaDeAsistenciaService servicio(Instant ahora) {
        Clock reloj = new Clock() {
            @Override
            public Instant now() {
                return ahora;
            }

            @Override
            public LocalDate today() {
                throw new AssertionError("pasar lista no puede depender de la fecha del servidor");
            }
        };
        var accesoEvento = new AccesoEventoService(progresoPort, nivelPort, cursoPort, (u, t) -> false, (c, u) -> false);
        var audiencia = new AudienciaDelEventoService(nivelPort, progresoPort, audienciaMasivaPort, celulaPort, cursoPort,
                (u, t) -> false);
        var acceso = new AccesoALaListaService(accesoEvento, loadEventoPort, loadExcepcionPort);
        var personas = new PersonasConvocadasService(audiencia, confirmacionPort, historialPort, loadLista, personasPort);
        return new ListaDeAsistenciaService(acceso, personas, audiencia, loadLista, saveLista, personasPort, reloj);
    }
}
