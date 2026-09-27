package com.renaser.os.calendar.application.services;

import com.renaser.os.calendar.application.ports.out.confirmacion.LoadConfirmacionPort;
import com.renaser.os.calendar.domain.model.evento.EventoId;
import com.renaser.os.calendar.domain.model.recordatorio.RecordatorioEvento;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Quienes dijeron "Voy" a la ocurrencia de cada recordatorio de un lote de despacho (D-189).
 *
 * <p>Se lee AL DESPACHAR y no al confirmar: vale el estado de ese momento. Si la persona cambio a
 * "No voy", el aviso ya no se trata como cubierto; si dijo "Voy" despues de encolado, si. Una
 * consulta por evento del lote, no una por recordatorio.
 */
final class AsistenciasConfirmadasDelLote {

    private final Set<String> claves;

    private AsistenciasConfirmadasDelLote(Set<String> claves) {
        this.claves = claves;
    }

    static AsistenciasConfirmadasDelLote de(List<RecordatorioEvento> recordatorios,
                                            LoadConfirmacionPort loadConfirmacionPort) {
        Map<EventoId, List<Instant>> ocurrenciasPorEvento = recordatorios.stream()
                .collect(Collectors.groupingBy(RecordatorioEvento::eventoId,
                        Collectors.mapping(RecordatorioEvento::inicioOcurrencia, Collectors.toList())));
        Set<String> claves = new HashSet<>();
        ocurrenciasPorEvento.forEach((eventoId, ocurrencias) -> loadConfirmacionPort
                .confirmadosAsistencia(eventoId, ocurrencias.stream().distinct().toList())
                .forEach(clave -> claves.add(eventoId.value() + "|" + clave)));
        return new AsistenciasConfirmadasDelLote(claves);
    }

    /** Clave con el mismo formato que {@link LoadConfirmacionPort#confirmadosAsistencia}, con el evento delante. */
    boolean incluye(RecordatorioEvento recordatorio) {
        return claves.contains(recordatorio.eventoId().value() + "|" + recordatorio.inicioOcurrencia() + "|"
                + recordatorio.usuarioId());
    }
}
