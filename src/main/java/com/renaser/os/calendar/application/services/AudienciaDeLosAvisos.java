package com.renaser.os.calendar.application.services;

import com.renaser.os.calendar.application.ports.out.confirmacion.LoadConfirmacionPort;
import com.renaser.os.calendar.domain.model.confirmacion.Confirmacion;
import com.renaser.os.calendar.domain.model.confirmacion.EstadoConfirmacion;
import com.renaser.os.calendar.domain.model.evento.EstadoEvento;
import com.renaser.os.calendar.domain.model.evento.Evento;
import com.renaser.os.calendar.domain.model.evento.EventoId;
import com.renaser.os.calendar.domain.model.recordatorio.RecordatorioEvento;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Qué pasa al despachar el aviso de un evento con audiencia elegida (spec 044). Dos cosas, decididas AL DESPACHAR y no al generar (como la
 * elegibilidad de la 020, {@link ElegibilidadDeLosAvisos}):
 * <ul>
 *   <li>FR-023: si la persona ya no está en la audiencia (salió del grupo, dejó de ser alumna), su aviso se cancela con
 *       {@link RecordatorioEvento#MOTIVO_FUERA_DE_LA_AUDIENCIA}.</li>
 *   <li>FR-022 (P2 del dueño): si respondió «No voy» a esa fecha, ningún recordatorio de esa fecha le llega (ni app, ni chat, ni WhatsApp): se
 *       cancela con {@link RecordatorioEvento#MOTIVO_DIJO_NO_VOY}. El aviso de «evento nuevo» no se cancela así: todavía no respondió nada.</li>
 * </ul>
 * Una lectura de la audiencia por evento del lote y una de respuestas por fecha, nunca una por aviso. Un evento sin audiencia elegida no se toca.
 */
@Component
class AudienciaDeLosAvisos {

    private final AudienciaDelEventoService audiencia;
    private final LoadConfirmacionPort confirmaciones;

    AudienciaDeLosAvisos(AudienciaDelEventoService audiencia, LoadConfirmacionPort confirmaciones) {
        this.audiencia = audiencia;
        this.confirmaciones = confirmaciones;
    }

    /** Por qué se cancela cada aviso del lote (id → motivo); los que no están en el mapa siguen su camino. */
    Map<Long, String> aCancelar(List<RecordatorioEvento> pendientes, Map<EventoId, Optional<Evento>> eventos) {
        Map<EventoId, Set<UserId>> miembros = new HashMap<>();
        Map<String, Set<UserId>> noVan = new HashMap<>();
        Map<Long, String> motivos = new HashMap<>();
        for (RecordatorioEvento recordatorio : pendientes) {
            Optional<Evento> evento = eventos.getOrDefault(recordatorio.eventoId(), Optional.empty())
                    .filter(e -> e.estado() != EstadoEvento.CANCELADO)
                    .filter(AudienciaDelEventoService::esElegida);
            if (evento.isEmpty()) {
                continue;
            }
            Evento e = evento.get();
            Set<UserId> dentro = miembros.computeIfAbsent(e.id(), id -> new HashSet<>(audiencia.destinatarios(e)));
            if (!dentro.contains(recordatorio.usuarioId())) {
                motivos.put(recordatorio.id(), RecordatorioEvento.MOTIVO_FUERA_DE_LA_AUDIENCIA);
                continue;
            }
            if (recordatorio.esAnuncio(e.creadoEn())) {
                continue;
            }
            Set<UserId> noVanAEsaFecha = noVan.computeIfAbsent(e.id() + "|" + recordatorio.inicioOcurrencia(),
                    clave -> quienesNoVan(e.id(), recordatorio.inicioOcurrencia()));
            if (noVanAEsaFecha.contains(recordatorio.usuarioId())) {
                motivos.put(recordatorio.id(), RecordatorioEvento.MOTIVO_DIJO_NO_VOY);
            }
        }
        return motivos;
    }

    private Set<UserId> quienesNoVan(EventoId evento, Instant ocurrencia) {
        Set<UserId> noVan = new HashSet<>();
        for (Confirmacion c : confirmaciones.deOcurrencia(evento, ocurrencia)) {
            if (c.estado() == EstadoConfirmacion.NO_ASISTE) {
                noVan.add(c.usuarioId());
            }
        }
        return noVan;
    }
}
