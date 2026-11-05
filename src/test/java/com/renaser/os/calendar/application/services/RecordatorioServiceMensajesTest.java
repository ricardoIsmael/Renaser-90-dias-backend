package com.renaser.os.calendar.application.services;

import com.renaser.os.calendar.application.ports.out.celula.ConsultarMiembrosCelulaPort;
import com.renaser.os.calendar.application.ports.out.curso.ResolverAudienciaCursoPort;
import com.renaser.os.calendar.application.ports.out.elegibilidad.ConsultarElegibilidadEventoPort;
import com.renaser.os.calendar.application.ports.out.evento.LoadEventoPort;
import com.renaser.os.calendar.application.ports.out.evento.LoadExcepcionPort;
import com.renaser.os.calendar.application.ports.out.nivelmembresia.LoadNivelMembresiaPort;
import com.renaser.os.calendar.application.ports.out.participante.ConsultarProgresoParticipanteCalendarPort;
import com.renaser.os.calendar.application.ports.out.participante.ResolverAudienciaMasivaPort;
import com.renaser.os.calendar.application.ports.out.recordatorio.SaveRecordatorioPort;
import com.renaser.os.calendar.domain.model.evento.Evento;
import com.renaser.os.calendar.domain.model.evento.EventoId;
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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * La cola de recordatorios tambien lleva los avisos de MENSAJE (chat y WhatsApp; spec 041, FR-014): 1 dia, 1 hora, 30 y 10 minutos antes, independientes de
 * las reglas del aviso del telefono. Las pruebas de siempre de {@code RecordatorioServiceTest} corren sin avisos de mensaje (lista vacia) y siguen igual.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RecordatorioServiceMensajesTest {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    /** 13:50 en Lima: la fecha UTC (10 sep, 18:50) y la local coinciden, pero el evento cae en otro dia UTC que el local. */
    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-09-10T18:50:00Z"));
    /** 20:00 del 11 en Lima = 01:00 UTC del 12. */
    private static final Instant INICIO_EN_DOS_DIAS = Instant.parse("2026-09-12T01:00:00Z");
    private static final List<Integer> LOS_CUATRO = List.of(1440, 60, 30, 10);

    @Mock
    private LoadEventoPort loadEventoPort;
    @Mock
    private LoadExcepcionPort loadExcepcionPort;
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

    private final UserId aprendiz = UserId.of(UUID.randomUUID());
    private final EventoId eventoId = EventoId.of(UUID.randomUUID());
    private RecordatorioService service;

    @BeforeEach
    void setUp() {
        var audiencia = new AudienciaDelEventoService(nivelPort, progresoPort, audienciaMasivaPort, celulaPort, cursoPort,
                elegibilidadPort, new com.renaser.os.users.api.AlcanceDeFormacionPermisivo());
        service = new RecordatorioService(loadEventoPort, loadExcepcionPort, saveRecordatorioPort, audiencia,
                new PlanDeMensajesDelEvento(LOS_CUATRO, CLOCK), CLOCK,
                org.springframework.transaction.support.TransactionOperations.withoutTransaction());
        when(audienciaMasivaPort.traineesActivos()).thenReturn(List.of(aprendiz));
        when(saveRecordatorioPort.encolarSiFalta(anyList())).thenAnswer(inv -> ((List<?>) inv.getArgument(0)).size());
    }

    // spec 041 FR-014, SC-008
    @Test
    @DisplayName("una sesion con tiempo genera los cuatro avisos de mensaje, ademas de los dos del telefono que ya tenia")
    void generaLosCuatroAvisosDeMensaje() {
        when(loadEventoPort.candidatosParaRecordatorios(any(), any(), any())).thenReturn(List.of(sesion(INICIO_EN_DOS_DIAS, false)));

        service.generar(CLOCK.now());

        List<RecordatorioEvento> filas = todasLasFilas();
        assertThat(filas).filteredOn(RecordatorioEvento::esDeMensaje).extracting(RecordatorioEvento::mensajeMinAntes)
                .containsExactlyInAnyOrder(1440, 60, 30, 10);
        assertThat(filas).filteredOn(RecordatorioEvento::avisoEnApp).hasSize(2)
                .allSatisfy(fila -> assertThat(fila.esDeMensaje()).as("los del telefono no son de mensaje").isFalse());
    }

    // spec 041 FR-014
    @Test
    @DisplayName("los avisos de mensaje NO son avisos del telefono: la app no recibe tres avisos mas")
    void losAvisosDeMensajeNoSonAvisosDelTelefono() {
        when(loadEventoPort.candidatosParaRecordatorios(any(), any(), any())).thenReturn(List.of(sesion(INICIO_EN_DOS_DIAS, false)));

        service.generar(CLOCK.now());

        assertThat(todasLasFilas()).filteredOn(RecordatorioEvento::esDeMensaje).allSatisfy(fila -> assertThat(fila.avisoEnApp()).isFalse());
    }

    // spec 041 FR-014, SC-008
    @Test
    @DisplayName("un evento creado 20 minutos antes de empezar solo genera el de 10 minutos")
    void unEventoCreadoVeinteMinutosAntesSoloTieneElDeDiez() {
        Instant inicio = CLOCK.now().plusSeconds(20 * 60);
        when(loadEventoPort.candidatosParaRecordatorios(any(), any(), any())).thenReturn(List.of(sesion(inicio, false)));

        service.generar(CLOCK.now());

        assertThat(todasLasFilas()).filteredOn(RecordatorioEvento::esDeMensaje).extracting(RecordatorioEvento::mensajeMinAntes)
                .containsExactly(10);
    }

    // spec 041 FR-014
    @Test
    @DisplayName("son independientes de las reglas del evento: sin reglas propias el evento sigue teniendo sus avisos de mensaje")
    void sonIndependientesDeLasReglasDelTelefono() {
        when(loadEventoPort.candidatosParaRecordatorios(any(), any(), any())).thenReturn(List.of(sesion(INICIO_EN_DOS_DIAS, true)));

        service.generar(CLOCK.now());

        List<RecordatorioEvento> filas = todasLasFilas();
        assertThat(filas).filteredOn(RecordatorioEvento::avisoEnApp).as("el evento no avisa al telefono").isEmpty();
        assertThat(filas).filteredOn(RecordatorioEvento::esDeMensaje).hasSize(4);
    }

    // spec 041 FR-014
    @Test
    @DisplayName("cada aviso cae a la hora que dice su antelacion, contada desde el inicio de la ocurrencia")
    void cadaAvisoCaeAsuHora() {
        when(loadEventoPort.candidatosParaRecordatorios(any(), any(), any())).thenReturn(List.of(sesion(INICIO_EN_DOS_DIAS, true)));

        service.generar(CLOCK.now());

        assertThat(todasLasFilas()).filteredOn(RecordatorioEvento::esDeMensaje).extracting(RecordatorioEvento::enviarEn)
                .containsExactlyInAnyOrder(INICIO_EN_DOS_DIAS.minusSeconds(86_400), INICIO_EN_DOS_DIAS.minusSeconds(3_600),
                        INICIO_EN_DOS_DIAS.minusSeconds(1_800), INICIO_EN_DOS_DIAS.minusSeconds(600));
    }

    // spec 041 FR-014, FR-016
    @Test
    @DisplayName("issue #85: un evento creado 15 minutos antes recibe el de 10 minutos aunque el barrido pase justo a esa hora")
    void elDeDiezSaleAunqueElBarridoPaseJustoASuHora() {
        Instant inicio = Instant.parse("2026-09-10T17:40:00Z");
        Evento creadoALas1225 = sesion(inicio, true, FixedClock.at(Instant.parse("2026-09-10T17:25:10Z")));
        when(loadEventoPort.candidatosParaRecordatorios(any(), any(), any())).thenReturn(List.of(creadoALas1225));

        // El barrido corre en punto: a las 12:30:00 (Lima) el «10 min antes» ya no es posterior a «ahora».
        service.generar(Instant.parse("2026-09-10T17:30:00.001Z"));

        assertThat(todasLasFilas()).filteredOn(RecordatorioEvento::esDeMensaje).extracting(RecordatorioEvento::mensajeMinAntes)
                .containsExactly(10);
    }

    // spec 041 FR-014, FR-016
    @Test
    @DisplayName("issue #85: lo que ya habia pasado cuando se creo el evento sigue sin generarse")
    void loQueYaHabiaPasadoAlCrearloNoSeGenera() {
        Evento creadoALas1231 = sesion(Instant.parse("2026-09-10T17:40:00Z"), true, FixedClock.at(Instant.parse("2026-09-10T17:31:00Z")));
        when(loadEventoPort.candidatosParaRecordatorios(any(), any(), any())).thenReturn(List.of(creadoALas1231));

        service.generar(Instant.parse("2026-09-10T17:35:00Z"));

        assertThat(todasLasFilas()).filteredOn(RecordatorioEvento::esDeMensaje).as("el de 10 min era a las 12:30, antes de crearlo").isEmpty();
    }

    // spec 041 FR-014, FR-016
    @Test
    @DisplayName("issue #85: un aviso con mas de 15 minutos de atraso no se genera; el que lleva menos, si")
    void elQueLlevaMasDeQuinceMinutosNoSeGenera() {
        Evento creadoTemprano = sesion(Instant.parse("2026-09-10T17:50:00Z"), true, FixedClock.at(Instant.parse("2026-09-10T15:00:00Z")));
        when(loadEventoPort.candidatosParaRecordatorios(any(), any(), any())).thenReturn(List.of(creadoTemprano));

        // A las 12:40 (Lima): el de 30 min (12:20) lleva 20 de atraso y no se genera; el de 10 min (12:40) entra.
        service.generar(Instant.parse("2026-09-10T17:40:00.001Z"));

        assertThat(todasLasFilas()).filteredOn(RecordatorioEvento::esDeMensaje).extracting(RecordatorioEvento::mensajeMinAntes)
                .containsExactly(10);
    }

    // spec 041 FR-014
    @Test
    @DisplayName("issue #86: un evento que falla al encolar no tumba el barrido: los demas eventos tienen sus avisos")
    void unEventoQueFallaNoTumbaElBarrido() {
        Evento malo = Evento.crear(EventoId.of(UUID.randomUUID()), "Falla", null, INICIO_EN_DOS_DIAS, 60, LIMA, TipoUbicacion.MEET,
                "https://meet.google.com/abc-defg-hij", TipoAudiencia.TODOS, null, null, null, TipoEvento.SESION_ESPECIAL,
                false, false, true, null, Set.of(), List.of(), aprendiz, CLOCK);
        when(loadEventoPort.candidatosParaRecordatorios(any(), any(), any())).thenReturn(List.of(malo, sesion(INICIO_EN_DOS_DIAS, true)));
        when(saveRecordatorioPort.encolarSiFalta(anyList())).thenAnswer(inv -> {
            List<RecordatorioEvento> filas = inv.getArgument(0);
            if (filas.get(0).eventoId().equals(malo.id())) {
                throw new org.springframework.dao.DataIntegrityViolationException("ON CONFLICT DO UPDATE command cannot affect row a second time");
            }
            return filas.size();
        });

        int creados = service.generar(CLOCK.now());

        assertThat(creados).as("los cuatro avisos de mensaje del evento sano").isEqualTo(4);
    }

    private List<RecordatorioEvento> todasLasFilas() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<RecordatorioEvento>> filas = ArgumentCaptor.forClass(List.class);
        verify(saveRecordatorioPort, atLeastOnce()).encolarSiFalta(filas.capture());
        List<RecordatorioEvento> todas = new ArrayList<>();
        filas.getAllValues().forEach(todas::addAll);
        return todas;
    }

    /** {@code sinReglas}: el evento decide no avisar al telefono (reglas propias vacias). */
    private Evento sesion(Instant inicio, boolean sinReglas) {
        return sesion(inicio, sinReglas, CLOCK);
    }

    /** {@code creadoCon}: el reloj del momento en que se creo el evento (FR-014 mira si el aviso ya habia pasado entonces). */
    private Evento sesion(Instant inicio, boolean sinReglas, FixedClock creadoCon) {
        return Evento.crear(eventoId, "Clase de manifestación", null, inicio, 60, LIMA, TipoUbicacion.MEET,
                "https://meet.google.com/abc-defg-hij", TipoAudiencia.TODOS, null, null, null, TipoEvento.SESION_ESPECIAL,
                false, false, sinReglas, null, Set.of(), List.of(), aprendiz, creadoCon);
    }
}
