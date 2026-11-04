package com.renaser.os.calendar.domain.model.recordatorio;

import com.renaser.os.calendar.domain.model.evento.EventoId;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.time.Instant;
import java.util.Objects;

/**
 * Una fila de la cola {@code recordatorios_evento} (tabla de alto volumen, PK bigint
 * IDENTITY). Puerto directo de {@code event_reminders} (repo viejo): IDEMPOTENTE via la
 * clave unica {@code (evento_id, inicio_ocurrencia, usuario_id, enviar_en)} — el
 * adaptador de persistencia hace INSERT ... ON CONFLICT DO NOTHING, nunca lanza si la fila
 * ya existe.
 *
 * <p>Motivos de cancelacion — mismo vocabulario que {@code MOTIVO} en reminderService.ts.
 */
@Getter
@Accessors(fluent = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode(of = "id")
public final class RecordatorioEvento {

    /** Historico: ya no se escribe desde D-189 (confirmar "Voy" dejo de apagar avisos), pero
     * quedan filas viejas con este motivo en {@code recordatorios_evento}. */
    public static final String MOTIVO_ASISTIRA = "confirmo_asistencia";
    public static final String MOTIVO_NO_ELEGIBLE = "dejo_de_ser_elegible";
    public static final String MOTIVO_EVENTO_CANCELADO = "evento_cancelado";

    private final Long id;
    private final EventoId eventoId;
    private final Instant inicioOcurrencia;
    private final UserId usuarioId;
    private final Instant enviarEn;
    private Instant enviadoEn;
    private String motivoCancelacion;
    private final Instant creadoEn;
    /** Si la fila es un aviso del telefono (bandeja y push), el de siempre. {@code false}: existe solo para el mensaje (spec 041). */
    private final boolean avisoEnApp;
    /** Minutos antes del inicio de este aviso de MENSAJE (chat y WhatsApp; spec 041), o {@code null} si la fila no es de mensaje. */
    private final Integer mensajeMinAntes;

    public static RecordatorioEvento programar(EventoId eventoId, Instant inicioOcurrencia, UserId usuarioId,
                                                Instant enviarEn, Clock clock) {
        return new RecordatorioEvento(null, Objects.requireNonNull(eventoId), Objects.requireNonNull(inicioOcurrencia),
                Objects.requireNonNull(usuarioId), Objects.requireNonNull(enviarEn), null, null, clock.now(), true, null);
    }

    /**
     * Un aviso de MENSAJE (spec 041): sale por el chat de soporte y por WhatsApp, y NO es un aviso del telefono (esos siguen siendo los de
     * las reglas del evento). Si una regla del evento cae en el mismo instante, la cola junta las dos marcas en una sola fila.
     */
    public static RecordatorioEvento programarMensaje(EventoId eventoId, Instant inicioOcurrencia, UserId usuarioId,
                                                       Instant enviarEn, int minutosAntes, Clock clock) {
        if (minutosAntes <= 0) {
            throw new IllegalArgumentException("minutosAntes tiene que ser positivo");
        }
        return new RecordatorioEvento(null, Objects.requireNonNull(eventoId), Objects.requireNonNull(inicioOcurrencia),
                Objects.requireNonNull(usuarioId), Objects.requireNonNull(enviarEn), null, null, clock.now(), false,
                minutosAntes);
    }

    public static RecordatorioEvento rehydrate(Long id, EventoId eventoId, Instant inicioOcurrencia,
                                                UserId usuarioId, Instant enviarEn, Instant enviadoEn,
                                                String motivoCancelacion, Instant creadoEn) {
        return rehydrate(id, eventoId, inicioOcurrencia, usuarioId, enviarEn, enviadoEn, motivoCancelacion, creadoEn,
                true, null);
    }

    public static RecordatorioEvento rehydrate(Long id, EventoId eventoId, Instant inicioOcurrencia,
                                                UserId usuarioId, Instant enviarEn, Instant enviadoEn,
                                                String motivoCancelacion, Instant creadoEn, boolean avisoEnApp,
                                                Integer mensajeMinAntes) {
        return new RecordatorioEvento(id, eventoId, inicioOcurrencia, usuarioId, enviarEn, enviadoEn,
                motivoCancelacion, creadoEn, avisoEnApp, mensajeMinAntes);
    }

    public void marcarEnviado(Clock clock) {
        this.enviadoEn = clock.now();
    }

    public void cancelar(String motivo) {
        this.motivoCancelacion = Objects.requireNonNull(motivo, "motivo es obligatorio");
    }

    public boolean pendiente() {
        return enviadoEn == null && motivoCancelacion == null;
    }

    /** Si esta fila es tambien un aviso de mensaje (chat y WhatsApp, spec 041). */
    public boolean esDeMensaje() {
        return mensajeMinAntes != null;
    }

    /**
     * Un recordatorio es el ANUNCIO de "evento nuevo" cuando su clave es fija
     * ({@code enviarEn = inicioOcurrencia = creadoEn del evento}, ver
     * {@code GenerarRecordatoriosScheduler#anunciar}) — nunca hace falta una columna aparte:
     * un recordatorio de verdad SIEMPRE nace con {@code enviarEn} en el futuro respecto a la
     * creacion del evento.
     */
    public boolean esAnuncio(Instant eventoCreadoEn) {
        return !enviarEn.isAfter(eventoCreadoEn);
    }
}
