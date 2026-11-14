package com.renaser.os.calendar.infrastructure.adapter.out.persistence.audiencia;

import com.renaser.os.calendar.application.ports.out.audiencia.ColoresDelSemaforoPort;
import com.renaser.os.calendar.domain.model.evento.audiencia.ColorDeAudiencia;
import com.renaser.os.points.api.DiaDelSemaforo;
import com.renaser.os.points.api.EstadoDiaSemaforo;
import com.renaser.os.points.api.SemaforoFinder;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * El color de cada persona para una audiencia: el del ÚLTIMO DÍA CERRADO CON DATOS (spec 053, FR-004; decisiones D4 y D5 del dueño, 2026-11-14).
 * Se lee de la ventana vigente de {@link SemaforoFinder#vigenteDe} (los siete días cerrados que terminan ayer en la zona de cada persona), una lectura
 * para todos (FR-012). Si ayer no tuvo datos (domingo, feriado, día sin nada programado), vale el último día anterior que sí los tuvo. Jamás el día en
 * curso: temprano todos se ven incompletos. «Sin datos» no es un color que se pueda elegir (P8).
 */
@Component
class ColoresDelSemaforoAdapter implements ColoresDelSemaforoPort {

    private final SemaforoFinder semaforo;

    ColoresDelSemaforoAdapter(SemaforoFinder semaforo) {
        this.semaforo = semaforo;
    }

    @Override
    public Map<UserId, ColorDeAudiencia> colores(Collection<UserId> personas) {
        return leer(personas).colores();
    }

    @Override
    public Lectura leer(Collection<UserId> personas) {
        if (personas.isEmpty()) {
            return new Lectura(Map.of(), null);
        }
        Map<UserId, ColorDeAudiencia> resultado = new HashMap<>();
        LocalDate fecha = null;
        for (var entrada : semaforo.vigenteDe(personas).entrySet()) {
            List<DiaDelSemaforo> dias = entrada.getValue().dias();
            for (int i = dias.size() - 1; i >= 0; i--) {
                DiaDelSemaforo dia = dias.get(i);
                if (dia.estado() != EstadoDiaSemaforo.MEDIDO) {
                    continue;
                }
                switch (dia.color()) {
                    case VERDE -> resultado.put(entrada.getKey(), ColorDeAudiencia.VERDE);
                    case AMARILLO -> resultado.put(entrada.getKey(), ColorDeAudiencia.AMARILLO);
                    case ROJO -> resultado.put(entrada.getKey(), ColorDeAudiencia.ROJO);
                    default -> {
                        // SIN_DATOS: no entra por ningún color.
                    }
                }
                if (fecha == null || dia.fecha().isAfter(fecha)) {
                    fecha = dia.fecha();
                }
                break;
            }
        }
        return new Lectura(resultado, fecha);
    }
}
