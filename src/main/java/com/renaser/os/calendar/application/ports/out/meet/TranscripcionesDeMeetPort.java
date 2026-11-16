package com.renaser.os.calendar.application.ports.out.meet;

import com.renaser.os.calendar.domain.model.evento.EventoId;
import com.renaser.os.calendar.domain.model.meet.transcripcion.DiagnosticoDeReunion;
import com.renaser.os.calendar.domain.model.meet.transcripcion.PendienteDeTranscripcion;
import com.renaser.os.calendar.domain.model.meet.transcripcion.TranscripcionDeSesion;
import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Las transcripciones guardadas de las sesiones por Meet (spec 047). La implementación cifra el texto y el enlace a la grabación al guardar y los
 * descifra al leer (FR-010); nada de eso queda en claro en la base.
 */
public interface TranscripcionesDeMeetPort {

    /** Las salas creadas cuya sesión terminó hace 5 minutos o más (y menos de 29 días) y todavía no tienen la transcripción resuelta. */
    List<PendienteDeTranscripcion> pendientes(Instant ahora, int limite);

    /** La sala de una fecha de un evento, aunque su transcripción ya esté resuelta (issue #151: para volver a leer qué pasó). */
    Optional<PendienteDeTranscripcion> salaDeLaFecha(EventoId eventoId, LocalDate fecha);

    void guardar(TranscripcionDeSesion transcripcion);

    /** La de una fecha de un evento, con el texto descifrado. */
    Optional<TranscripcionDeSesion> deLaFecha(EventoId eventoId, LocalDate fecha);

    /** El estado de cada fecha del evento, sin texto (FR-012). */
    List<EstadoPorFecha> estadosDelEvento(EventoId eventoId);

    /** Las listas que vencieron, o cuya sala se borró (FR-014, FR-015). Sin texto: se van a borrar. */
    List<ParaBorrar> paraBorrar(Instant ahora, int limite);

    /** Vacía lo cifrado y deja la constancia {@code BORRADA}. */
    void borrar(java.util.UUID transcripcionId, Instant ahora);

    /** Constancia de quién sacó ({@code LECTURA}) o borró a pedido ({@code BORRADO}) una transcripción, con su motivo (FR-013, FR-016). */
    void registrarAcceso(UserId actor, EventoId eventoId, LocalDate fecha, String accion, String motivo, Instant en);

    int purgarLecturasAnterioresA(Instant limite);

    record EstadoPorFecha(LocalDate fecha, String estado, String motivo, Instant obtenidaEn, Instant borrarDespuesDe, boolean grabacion,
                          DiagnosticoDeReunion diagnostico) {
    }

    record ParaBorrar(java.util.UUID transcripcionId, EventoId eventoId, LocalDate fecha) {
    }
}
