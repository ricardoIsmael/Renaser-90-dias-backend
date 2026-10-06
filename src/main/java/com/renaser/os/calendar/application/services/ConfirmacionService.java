package com.renaser.os.calendar.application.services;

import com.renaser.os.calendar.application.ports.in.confirmacion.ConfirmarAsistenciaUseCase;
import com.renaser.os.calendar.application.ports.out.confirmacion.HistorialDeRespuestasPort;
import com.renaser.os.calendar.application.ports.out.confirmacion.LoadConfirmacionPort;
import com.renaser.os.calendar.application.ports.out.confirmacion.SaveConfirmacionPort;
import com.renaser.os.calendar.application.ports.out.evento.LoadEventoPort;
import com.renaser.os.calendar.application.ports.out.participante.ConsultarProgresoParticipanteCalendarPort.ProgresoParticipanteCalendar;
import com.renaser.os.calendar.domain.model.confirmacion.Confirmacion;
import com.renaser.os.calendar.domain.model.confirmacion.EstadoConfirmacion;
import com.renaser.os.calendar.domain.model.evento.Evento;
import com.renaser.os.calendar.domain.model.evento.EventoId;
import com.renaser.os.calendar.domain.model.evento.ExpansorOcurrencias;
import com.renaser.os.calendar.domain.model.evento.Ocurrencia;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;

@Service
public class ConfirmacionService implements ConfirmarAsistenciaUseCase {

    /** Mismo margen que isRealOccurrence()/setRsvp() del repo viejo. */
    private static final long TOLERANCIA_OCURRENCIA_MS = 180_000;
    /** "No puedes confirmar asistencia a una ocurrencia de dias pasados" — mismo margen (12h) que setRsvp(). */
    private static final long MARGEN_OCURRENCIA_PASADA_HORAS = 12;

    private final LoadEventoPort loadEventoPort;
    private final SaveConfirmacionPort saveConfirmacionPort;
    private final LoadConfirmacionPort loadConfirmacionPort;
    private final HistorialDeRespuestasPort historialPort;
    private final AccesoEventoService accesoEventoService;
    private final Clock clock;

    public ConfirmacionService(LoadEventoPort loadEventoPort, SaveConfirmacionPort saveConfirmacionPort,
                                LoadConfirmacionPort loadConfirmacionPort, HistorialDeRespuestasPort historialPort,
                                AccesoEventoService accesoEventoService, Clock clock) {
        this.loadEventoPort = loadEventoPort;
        this.saveConfirmacionPort = saveConfirmacionPort;
        this.loadConfirmacionPort = loadConfirmacionPort;
        this.historialPort = historialPort;
        this.accesoEventoService = accesoEventoService;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void confirmar(UserId actorId, EventoId eventoId, Instant inicioOcurrencia, EstadoConfirmacion estado) {
        ProgresoParticipanteCalendar progreso = accesoEventoService.requireProgreso(actorId);
        Evento evento = loadEventoPort.byId(eventoId)
                .orElseThrow(() -> new NoSuchElementException("Evento no encontrado: " + eventoId));

        var visor = accesoEventoService.buildVisor(progreso);
        if (!accesoEventoService.puedeAcceder(actorId, progreso, visor, evento)) {
            throw new NotAuthorizedException("No tienes acceso a este evento");
        }
        if (!esOcurrenciaReal(evento, inicioOcurrencia)) {
            throw new IllegalArgumentException("inicioOcurrencia no corresponde a una ocurrencia real de este evento");
        }

        Instant inicioHoy = clock.today().atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
        if (inicioOcurrencia.isBefore(inicioHoy.minusSeconds(MARGEN_OCURRENCIA_PASADA_HORAS * 3600))) {
            throw new IllegalStateException("No puedes confirmar asistencia a una ocurrencia de dias pasados");
        }

        Instant ahora = clock.now();
        boolean cambia = loadConfirmacionPort.estadoDe(eventoId, inicioOcurrencia, actorId)
                .map(anterior -> anterior != estado).orElse(true);
        Confirmacion respuesta = new Confirmacion(eventoId, inicioOcurrencia, actorId, estado, ahora, ahora);
        saveConfirmacionPort.upsert(respuesta);
        // D-256: el historial guarda cada CAMBIO de respuesta, desde la V93 (las anteriores no tienen
        // historia). Repetir la misma respuesta (doble toque, reintento) no agrega fila.
        if (cambia) {
            historialPort.registrar(respuesta);
        }
        // D-189 (2026-09-26): aca se apagaban, con ASISTE, los recordatorios pendientes de esta
        // persona para esta ocurrencia (en transaccion propia, C-15). Se quito: la alarma local
        // existe solo en la app del telefono, y quien respondia "Voy" desde la web se quedaba sin
        // ningun aviso. Si el aviso sigue haciendo falta se decide al entregarlo, con los tokens de
        // ese momento (DespachoDeRecordatoriosService.despachar + notifications.RecordatorioEventoNotificationListener).
    }

    private boolean esOcurrenciaReal(Evento evento, Instant inicioOcurrencia) {
        if (!evento.esRecurrente()) {
            return Math.abs(evento.iniciaEn().toEpochMilli() - inicioOcurrencia.toEpochMilli()) <= TOLERANCIA_OCURRENCIA_MS;
        }
        Instant desde = inicioOcurrencia.minusSeconds(86_400);
        Instant hasta = inicioOcurrencia.plusSeconds(86_400);
        List<Ocurrencia> coincidencias = ExpansorOcurrencias.expandir(evento.iniciaEn(), evento.duracionMinutos(),
                evento.timezone(), evento.recurrencia(), desde, hasta, List.of());
        return coincidencias.stream().anyMatch(o ->
                Math.abs(o.inicioOcurrencia().toEpochMilli() - inicioOcurrencia.toEpochMilli()) <= TOLERANCIA_OCURRENCIA_MS);
    }
}
