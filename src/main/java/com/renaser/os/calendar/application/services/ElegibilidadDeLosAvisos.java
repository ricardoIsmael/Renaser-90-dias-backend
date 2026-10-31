package com.renaser.os.calendar.application.services;

import com.renaser.os.calendar.domain.model.evento.Evento;
import com.renaser.os.calendar.domain.model.evento.EventoId;
import com.renaser.os.calendar.domain.model.evento.EstadoEvento;
import com.renaser.os.calendar.domain.model.evento.ReglasPorTipoEvento;
import com.renaser.os.calendar.domain.model.evento.TipoEvento;
import com.renaser.os.calendar.domain.model.recordatorio.RecordatorioEvento;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Qué pasa con un aviso que ya está en la cola cuando la persona deja de ser elegible (D-293). La Mentoría del
 * Alquimista exige semáforo verde: se evalúa con el semáforo de AHORA, así que quien estaba en verde cuando se
 * generó su recordatorio y a la hora de enviarlo ya no lo está, no lo recibe.
 *
 * <p>Se decide al DESPACHAR, no al generar: el mismo camino que usa la generación ({@code AudienciaDelEventoService})
 * y el mismo motivo que ya traía la cola desde el repo viejo ({@link RecordatorioEvento#MOTIVO_NO_ELEGIBLE}, que
 * ningún código escribía). El aviso se cancela con ese motivo y no se publica; no se borra ni se mueve nada más.
 * Si la persona vuelve a estar en verde antes de la hora de OTRO aviso del mismo evento, ese sí sale; el cancelado
 * no se resucita (su clave sigue ocupada, así que la generación tampoco lo vuelve a encolar).
 *
 * <p>Una sola lectura por tipo de evento y por lote, no una por aviso. Un evento cancelado no se pregunta: ya lo
 * cancela el despacho con su propio motivo.
 */
@Component
class ElegibilidadDeLosAvisos {

    private final AudienciaDelEventoService audiencia;

    ElegibilidadDeLosAvisos(AudienciaDelEventoService audiencia) {
        this.audiencia = audiencia;
    }

    /** Los ids de los recordatorios cuyo destinatario ya no pasa la elegibilidad del tipo de su evento. */
    Set<Long> dejaronDeSerElegibles(List<RecordatorioEvento> pendientes, Map<EventoId, Optional<Evento>> eventos) {
        Map<TipoEvento, Set<UserId>> personasPorTipo = new EnumMap<>(TipoEvento.class);
        for (RecordatorioEvento recordatorio : pendientes) {
            tipoQueExige(recordatorio, eventos).ifPresent(tipo -> personasPorTipo
                    .computeIfAbsent(tipo, clave -> new LinkedHashSet<>()).add(recordatorio.usuarioId()));
        }
        Map<TipoEvento, Set<UserId>> pasan = new EnumMap<>(TipoEvento.class);
        personasPorTipo.forEach((tipo, personas) -> pasan.put(tipo, audiencia.quienesPasan(personas, tipo)));

        Set<Long> ids = new HashSet<>();
        for (RecordatorioEvento recordatorio : pendientes) {
            tipoQueExige(recordatorio, eventos)
                    .filter(tipo -> !pasan.get(tipo).contains(recordatorio.usuarioId()))
                    .ifPresent(tipo -> ids.add(recordatorio.id()));
        }
        return ids;
    }

    /** El tipo de evento del recordatorio, si exige elegibilidad y el evento sigue en pie. */
    private static Optional<TipoEvento> tipoQueExige(RecordatorioEvento recordatorio,
                                                     Map<EventoId, Optional<Evento>> eventos) {
        return eventos.getOrDefault(recordatorio.eventoId(), Optional.empty())
                .filter(evento -> evento.estado() != EstadoEvento.CANCELADO)
                .map(Evento::tipoEvento)
                .filter(ReglasPorTipoEvento::requiereElegibilidad);
    }
}
