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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

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
    /**
     * Transaccion PROPIA (REQUIRES_NEW) para {@link #cancelarAvisosDeAsistencia} — C-15
     * (docs/informes/auditoria-seguridad-concurrencia-2026-09-01.html). Mismo criterio que
     * {@code EspirituService}/{@code ConversacionService}/{@code RegistroService}.
     */
    private final TransactionTemplate transaccionPropia;

    public ConfirmacionService(LoadEventoPort loadEventoPort, SaveConfirmacionPort saveConfirmacionPort,
                                SaveRecordatorioPort saveRecordatorioPort, AccesoEventoService accesoEventoService,
                                Clock clock, PlatformTransactionManager transactionManager) {
        this.loadEventoPort = loadEventoPort;
        this.saveConfirmacionPort = saveConfirmacionPort;
        this.saveRecordatorioPort = saveRecordatorioPort;
        this.accesoEventoService = accesoEventoService;
        this.clock = clock;
        this.transaccionPropia = new TransactionTemplate(transactionManager);
        this.transaccionPropia.setPropagationBehavior(Propagation.REQUIRES_NEW.value());
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
            cancelarAvisosDeAsistencia(actorId, eventoId, inicioOcurrencia);
        }
    }

    /**
     * C-15 (docs/informes/auditoria-seguridad-concurrencia-2026-09-01.html): antes, esta
     * cancelacion corria dentro de la MISMA transaccion que guarda la confirmacion, y el
     * {@code catch} se limitaba a loguear un warn con {@code ex.getMessage()}. El problema no
     * era el catch en si: {@code cancelarPorAsistencia} es un metodo {@code @Modifying} de
     * Spring Data JPA, y Spring Data envuelve todo metodo {@code @Modifying} con su propio
     * {@code @Transactional} — al PARTICIPAR (propagacion REQUIRED por defecto) de la
     * transaccion en curso de {@code confirmar()}, si ese metodo lanza, SU PROPIO advice
     * marca la transaccion compartida como rollback-only ANTES de que la excepcion llegue a
     * este catch. El warn nunca revierte esa marca: {@code confirmar()} sigue de largo,
     * termina "bien", y el commit posterior explota con {@code UnexpectedRollbackException}
     * — un error que no dice nada de la causa real, lejos de donde ocurrio.
     *
     * <p>La cancelacion corre ahora en su propia transaccion ({@link #transaccionPropia},
     * REQUIRES_NEW): si falla, la UNICA transaccion que se marca rollback-only es esa,
     * chica y aislada — la de {@code confirmar()} nunca se entera y puede comitear con
     * normalidad (intencion original: quien marca ASISTE no deberia perder su confirmacion
     * porque fallo un efecto secundario best-effort). El {@code catch} sigue existiendo
     * (nunca vacio, CLAUDE.MD §5.4.4) pero ahora cumple su proposito: loguear con la
     * EXCEPCION completa (no solo el mensaje) para que la causa real quede visible donde
     * ocurrio, y decidir explicitamente "seguir sin cancelar los avisos" — no dejar un
     * estado ambiguo.
     */
    private void cancelarAvisosDeAsistencia(UserId actorId, EventoId eventoId, Instant inicioOcurrencia) {
        try {
            transaccionPropia.executeWithoutResult(status -> saveRecordatorioPort.cancelarPorAsistencia(actorId,
                    eventoId, inicioOcurrencia, RecordatorioEvento.MOTIVO_ASISTIRA));
        } catch (RuntimeException ex) {
            log.warn("[ConfirmacionService.confirmar] no se pudieron cancelar los avisos de {}/{}/{}", eventoId,
                    inicioOcurrencia, actorId, ex);
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
