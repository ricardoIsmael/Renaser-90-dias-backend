package com.renaser.os.calendar.application.services;

import com.renaser.os.calendar.application.ports.out.celula.ConsultarMiembrosCelulaPort;
import com.renaser.os.calendar.application.ports.out.confirmacion.LoadConfirmacionPort;
import com.renaser.os.calendar.application.ports.out.curso.ResolverAudienciaCursoPort;
import com.renaser.os.calendar.application.ports.out.elegibilidad.ConsultarElegibilidadEventoPort;
import com.renaser.os.calendar.application.ports.out.evento.LoadEventoPort;
import com.renaser.os.calendar.application.ports.out.evento.LoadExcepcionPort;
import com.renaser.os.calendar.application.ports.out.nivelmembresia.LoadNivelMembresiaPort;
import com.renaser.os.calendar.application.ports.out.participante.ConsultarProgresoParticipanteCalendarPort;
import com.renaser.os.calendar.application.ports.out.participante.ConsultarProgresoParticipanteCalendarPort.ProgresoParticipanteCalendar;
import com.renaser.os.calendar.application.ports.out.participante.ResolverAudienciaMasivaPort;
import com.renaser.os.calendar.application.ports.out.recordatorio.SaveRecordatorioPort;
import com.renaser.os.calendar.domain.model.evento.Evento;
import com.renaser.os.calendar.domain.model.evento.EventoId;
import com.renaser.os.calendar.domain.model.evento.ReglaRecordatorio;
import com.renaser.os.calendar.domain.model.evento.RolUsuario;
import com.renaser.os.calendar.domain.model.evento.TipoAudiencia;
import com.renaser.os.calendar.domain.model.evento.TipoEvento;
import com.renaser.os.calendar.domain.model.evento.TipoUbicacion;
import com.renaser.os.calendar.domain.model.recordatorio.RecordatorioEvento;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecordatorioServiceTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-09-10T18:50:00Z"));

    @Mock
    private LoadEventoPort loadEventoPort;
    @Mock
    private LoadExcepcionPort loadExcepcionPort;
    @Mock
    private LoadConfirmacionPort loadConfirmacionPort;
    @Mock
    private SaveRecordatorioPort saveRecordatorioPort;
    @Mock
    private LoadNivelMembresiaPort nivelPort;
    @Mock
    private ConsultarProgresoParticipanteCalendarPort progresoPort;
    @Mock
    private ResolverAudienciaMasivaPort audienciaMasivaPort;
    @Mock
    private ConsultarMiembrosCelulaPort celulaPort;
    @Mock
    private ResolverAudienciaCursoPort cursoPort;
    @Mock
    private ConsultarElegibilidadEventoPort elegibilidadPort;

    private RecordatorioService service;
    private final UserId usuarioId = UserId.of(UUID.randomUUID());
    private final EventoId eventoId = EventoId.of(UUID.randomUUID());

    @BeforeEach
    void setUp() {
        var audiencia = new AudienciaDelEventoService(nivelPort, progresoPort, audienciaMasivaPort, celulaPort,
                cursoPort, elegibilidadPort);
        service = new RecordatorioService(loadEventoPort, loadExcepcionPort, saveRecordatorioPort, audiencia, CLOCK);
    }

    /**
     * HALLAZGO-A2 (E-362): con el adaptador anterior la elegibilidad respondia {@code false} a todo aprendiz y
     * una Mentoria "para todos" no generaba ningun recordatorio. Aca el puerto responde lo que responde el
     * adaptador de hoy ({@code ElegibilidadSegunAudienciaAdapter}: la audiencia decide).
     */
    @Test
    @DisplayName("E-362: una Mentoria del Alquimista para todos le genera su recordatorio al aprendiz")
    void laMentoriaGeneraElRecordatorioDelAprendiz() {
        Evento mentoria = Evento.crear(eventoId, "Mentoria", null, OCURRENCIA_NOCHE, 60, ZoneId.of("America/Lima"),
                TipoUbicacion.MEET, "https://meet.google.com/abc", TipoAudiencia.TODOS, null, null, null,
                TipoEvento.MENTORIA_ALQUIMISTA, false, false, false, null, Set.of(), List.of(), usuarioId, CLOCK);
        when(loadEventoPort.candidatosParaRecordatorios(any(), any(), any())).thenReturn(List.of(mentoria));
        when(audienciaMasivaPort.traineesActivos()).thenReturn(List.of(usuarioId));
        when(progresoPort.deParticipante(usuarioId)).thenReturn(Optional.of(new ProgresoParticipanteCalendar(12,
                ZoneId.of("America/Lima"), RolUsuario.TRAINEE, false, null)));
        when(elegibilidadPort.esElegible(usuarioId, TipoEvento.MENTORIA_ALQUIMISTA)).thenReturn(true);
        when(saveRecordatorioPort.encolarSiFalta(anyList())).thenAnswer(inv -> ((List<?>) inv.getArgument(0)).size());

        assertThat(service.generar(CLOCK.now())).as("el de 10 minutos antes").isEqualTo(1);
    }

    @Test
    void generarNoHaceNadaSiNoHayEventosCandidatos() {
        when(loadEventoPort.candidatosParaRecordatorios(any(), any(), any())).thenReturn(List.of());

        int creados = service.generar(CLOCK.now());

        assertThat(creados).isZero();
        verify(saveRecordatorioPort, never()).encolarSiFalta(anyList());
    }

    /** Ocurrencia a las 01:00 UTC del 11 = 20:00 del 10 en Lima: la fecha UTC y la local difieren (regla 03). */
    private static final Instant OCURRENCIA_NOCHE = Instant.parse("2026-09-11T01:00:00Z");

    /**
     * D-189: antes {@code generar()} dejaba afuera de la cola a quien ya habia dicho "Voy" (y
     * {@code confirmar()} cancelaba lo ya encolado). Quien respondia desde la web, sin alarma
     * local, no recibia ningun aviso. Ahora se encola igual; si el telefono lo cubre lo decide
     * {@code notifications} al entregar. Contra el codigo viejo esta prueba falla: no se encola nada.
     */
    @Test
    @DisplayName("D-189: generar encola los avisos aunque la persona ya haya dicho Voy")
    void generarEncolaAunqueLaPersonaYaDijoVoy() {
        Evento evento = Evento.crear(eventoId, "Sesion", null, OCURRENCIA_NOCHE, 60, ZoneId.of("America/Lima"),
                TipoUbicacion.MEET, "https://meet.google.com/abc", TipoAudiencia.TODOS, null, null, null,
                TipoEvento.ESPONTANEO, false, false, true, null, Set.of(),
                List.of(ReglaRecordatorio.minutosAntes(1, 30)), usuarioId, CLOCK);
        when(loadEventoPort.candidatosParaRecordatorios(any(), any(), any())).thenReturn(List.of(evento));
        when(audienciaMasivaPort.traineesActivos()).thenReturn(List.of(usuarioId));
        lenient().when(loadConfirmacionPort.confirmadosAsistencia(any(), anyList()))
                .thenReturn(Set.of(OCURRENCIA_NOCHE + "|" + usuarioId));
        when(saveRecordatorioPort.encolarSiFalta(anyList())).thenAnswer(inv -> ((List<?>) inv.getArgument(0)).size());

        int creados = service.generar(CLOCK.now());

        assertThat(creados).isEqualTo(1);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<RecordatorioEvento>> filas = ArgumentCaptor.forClass(List.class);
        verify(saveRecordatorioPort).encolarSiFalta(filas.capture());
        assertThat(filas.getValue()).extracting(RecordatorioEvento::usuarioId).containsExactly(usuarioId);
    }
}
