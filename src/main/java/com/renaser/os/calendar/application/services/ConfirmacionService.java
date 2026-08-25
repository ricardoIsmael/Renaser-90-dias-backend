package com.renaser.os.calendar.application.services;

import com.renaser.os.calendar.application.ports.in.confirmacion.ConfirmarAsistenciaUseCase;
import com.renaser.os.calendar.application.ports.out.confirmacion.SaveConfirmacionPort;
import com.renaser.os.calendar.application.ports.out.evento.LoadEventoPort;
import com.renaser.os.calendar.application.ports.out.participante.ConsultarProgresoParticipanteCalendarPort.ProgresoParticipanteCalendar;
import com.renaser.os.calendar.application.ports.out.recordatorio.SaveRecordatorioPort;
import com.renaser.os.calendar.domain.model.confirmacion.Confirmacion;
import com.renaser.os.calendar.domain.model.confirmacion.EstadoConfirmacion;
import com.renaser.os.calendar.domain.model.evento.Evento;
import com.renaser.os.calendar.domain.model.evento.EventoId;
import com.renaser.os.calendar.domain.model.evento.ExpansorOcurrencias;
import com.renaser.os.calendar.domain.model.evento.Ocurrencia;
import com.renaser.os.calendar.domain.model.recordatorio.RecordatorioEvento;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;

@Service
public class ConfirmacionService implements ConfirmarAsistenciaUseCase {

    private static final Logger log = LoggerFactory.getLogger(ConfirmacionService.class);
    /** Mismo margen que isRealOccurrence()/setRsvp() del repo viejo. */
    private static final long TOLERANCIA_OCURRENCIA_MS = 180_000;
    /** "No puedes confirmar asistencia a una ocurrencia de dias pasados" — mismo margen (12h) que setRsvp(). */
    private static final long MARGEN_OCURRENCIA_PASADA_HORAS = 12;

    private final LoadEventoPort loadEventoPort;
    private final SaveConfirmacionPort saveConfirmacionPort;
    private final SaveRecordatorioPort saveRecordatorioPort;
    private final AccesoEventoService accesoEventoService;
    private final Clock clock;

    public ConfirmacionService(LoadEventoPort loadEventoPort, SaveConfirmacionPort saveConfirmacionPort,
                                SaveRecordatorioPort saveRecordatorioPort, AccesoEventoService accesoEventoService,
                                Clock clock) {
        this.loadEventoPort = loadEventoPort;
        this.saveConfirmacionPort = saveConfirmacionPort;
        this.saveRecordatorioPort = saveRecordatorioPort;
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
        saveConfirmacionPort.upsert(new Confirmacion(eventoId, inicioOcurrencia, actorId, estado, ahora, ahora));

        // "Asistire" apaga los avisos pendientes de ESTA persona para ESTA ocurrencia — solo con ASISTE
        // (quien marca NO_ASISTE probablemente sigue queriendo que se le recuerde). Fuera del camino de
        // error: la confirmacion ya quedo guardada, mismo criterio que setRsvp() del repo viejo.
        if (estado == EstadoConfirmacion.ASISTE) {
            try {
                saveRecordatorioPort.cancelarPorAsistencia(actorId, eventoId, inicioOcurrencia,
                        RecordatorioEvento.MOTIVO_ASISTIRA);
            } catch (RuntimeException ex) {
                log.warn("[ConfirmacionService.confirmar] no se pudieron cancelar los avisos de {}/{}/{}: {}",
                        eventoId, inicioOcurrencia, actorId, ex.getMessage());
            }
        }
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
