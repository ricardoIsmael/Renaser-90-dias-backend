package com.renaser.os.calendar.application.ports.out.recordatorio;

import com.renaser.os.calendar.domain.model.evento.EventoId;
import com.renaser.os.calendar.domain.model.recordatorio.RecordatorioEvento;
import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.util.List;

public interface SaveRecordatorioPort {

    /** INSERT ... ON CONFLICT DO NOTHING sobre la UNIQUE (evento_id, inicio_ocurrencia,
     * usuario_id, enviar_en) — idempotente, dos pasadas del cron no duplican avisos.
     * Devuelve cuantas filas se insertaron de verdad. */
    int encolarSiFalta(List<RecordatorioEvento> recordatorios);

    void marcarEnviados(List<Long> ids, Instant enviadoEn);

    int cancelarPorIds(List<Long> ids, String motivo);

    /** cancelarPorAsistencia() del repo viejo — solo alcanza a `sentAt IS NULL`. */
    int cancelarPorAsistencia(UserId usuarioId, EventoId eventoId, Instant inicioOcurrencia, String motivo);

    /** cancelarPorOcurrencia() del repo viejo. */
    int cancelarPorOcurrencia(EventoId eventoId, Instant inicioOcurrencia, String motivo);

    /** borrarPendientes() del repo viejo: solo lo que aun no salio y no esta ya cancelado,
     * y solo con {@code enviarEn > ahora} — lo que estaba a punto de despacharse se respeta. */
    int borrarPendientesFuturos(EventoId eventoId, Instant ahora);
}
