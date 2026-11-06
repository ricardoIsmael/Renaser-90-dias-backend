package com.renaser.os.calendar.infrastructure.adapter.out.persistence.audiencia;

import com.renaser.os.calendar.application.ports.out.audiencia.ColoresDelSemaforoPort;
import com.renaser.os.calendar.domain.model.evento.audiencia.ColorDeAudiencia;
import com.renaser.os.points.api.SemaforoFinder;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * El semáforo vigente de cada persona, por el contrato público de {@code points} ({@link SemaforoFinder#vigenteDe}: los últimos 7 días cerrados en
 * su zona), el mismo que ve en Hoy (spec 044, FR-009). Una lectura para todos (FR-012). «Sin datos» no es un color que se pueda elegir (P8).
 */
@Component
class ColoresDelSemaforoAdapter implements ColoresDelSemaforoPort {

    private final SemaforoFinder semaforo;

    ColoresDelSemaforoAdapter(SemaforoFinder semaforo) {
        this.semaforo = semaforo;
    }

    @Override
    public Map<UserId, ColorDeAudiencia> colores(Collection<UserId> personas) {
        if (personas.isEmpty()) {
            return Map.of();
        }
        Map<UserId, ColorDeAudiencia> resultado = new HashMap<>();
        semaforo.vigenteDe(personas).forEach((persona, ventana) -> {
            switch (ventana.color()) {
                case VERDE -> resultado.put(persona, ColorDeAudiencia.VERDE);
                case AMARILLO -> resultado.put(persona, ColorDeAudiencia.AMARILLO);
                case ROJO -> resultado.put(persona, ColorDeAudiencia.ROJO);
                default -> {
                    // SIN_DATOS: no entra por ningún color.
                }
            }
        });
        return resultado;
    }
}
