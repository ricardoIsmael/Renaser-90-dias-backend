package com.renaser.os.habits.application.ports.in.radar;

import com.renaser.os.habits.domain.model.radar.MiRadar;
import com.renaser.os.habits.domain.model.radar.RegistroRadar;
import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.util.List;

/** radar.ts:340-390 (`getRadarHistory`, `RADAR_HISTORY_PAGE_SIZE=20`) — historial paginado por cursor. */
public interface ConsultarHistorialRadarUseCase {

    /** Autoservicio: actorId debe ser el propio participanteId. {@code cursor} null trae la pagina mas reciente. */
    HistorialRadarPage historial(UserId actorId, UserId participanteId, Instant cursor, int tamanoPagina);

    /**
     * Espejo de {@code RadarHistoryPage} (radar.ts:345-350): {@code siguienteCursor} es el
     * `creadoEn` de la entrada mas vieja de esta pagina, o null si no hay mas —
     * misma heuristica que el cliente viejo: "pagina llena implica que puede haber mas",
     * sin una consulta extra de conteo (radar.ts:384-387).
     *
     * <p>{@code miRadar} (spec 012): si la persona lo tiene encendido y a que horas. Va en la pagina porque quien lee
     * el historial —ella, su mentor, su lider— lo necesita para ponerle nombre a cada hora. {@code null} mientras la
     * persona va por un dia menor que 8: en la primera semana Mi Radar no aplica.
     */
    record HistorialRadarPage(List<RegistroRadar> entradas, Instant siguienteCursor, MiRadar miRadar) {
    }
}
