package com.renaser.os.points.application.services;

import com.renaser.os.points.api.ConteoDelDia;
import com.renaser.os.points.api.ConteoDiarioHabitosFinder;
import com.renaser.os.points.api.ConteoDiarioObjetivosFinder;
import com.renaser.os.points.api.DiaDelSemaforo;
import com.renaser.os.points.api.SemaforoDelDiaFinder;
import com.renaser.os.points.application.ports.out.semaforo.PausasDelSemaforoPort;
import com.renaser.os.points.domain.model.semaforo.CalendarioDeMedicion;
import com.renaser.os.points.domain.model.semaforo.CumplimientoDelDia;
import com.renaser.os.points.domain.model.semaforo.PausaDeMedicion;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.ProgramasActivadosFinder;
import com.renaser.os.users.api.ProgramasActivadosFinder.ProgramaActivado;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * El día en curso del semáforo, en vivo (D-223). Cuatro consultas para toda la colección (programas,
 * pausas, hábitos y objetivos), nunca una por persona. Solo lee: sin {@code @Transactional}, cada
 * consulta en la suya, porque quien llama es un barrido (regla 02 §4).
 */
@Service
public class SemaforoDelDiaService implements SemaforoDelDiaFinder {

    private final ProgramasActivadosFinder programasFinder;
    private final PausasDelSemaforoPort pausasPort;
    private final ConteoDiarioHabitosFinder habitosFinder;
    private final ConteoDiarioObjetivosFinder objetivosFinder;

    public SemaforoDelDiaService(ProgramasActivadosFinder programasFinder, PausasDelSemaforoPort pausasPort,
                                 ConteoDiarioHabitosFinder habitosFinder, ConteoDiarioObjetivosFinder objetivosFinder) {
        this.programasFinder = programasFinder;
        this.pausasPort = pausasPort;
        this.habitosFinder = habitosFinder;
        this.objetivosFinder = objetivosFinder;
    }

    @Override
    public Map<UserId, DiaDelSemaforo> delDia(Collection<UserId> participantes, LocalDate fecha) {
        Map<UserId, CalendarioDeMedicion> calendarios = calendariosDe(participantes);
        if (calendarios.isEmpty()) {
            return Map.of();
        }
        Map<UserId, List<ConteoDelDia>> habitos = habitosFinder.porParticipanteEntre(calendarios.keySet(), fecha, fecha);
        Map<UserId, List<ConteoDelDia>> objetivos =
                objetivosFinder.porParticipanteEntre(calendarios.keySet(), fecha, fecha);
        Map<UserId, DiaDelSemaforo> resultado = new LinkedHashMap<>();
        calendarios.forEach((id, calendario) -> resultado.put(id, calendario.seMide(fecha)
                ? CumplimientoDelDia.de(fecha, delDia(habitos.get(id), fecha), delDia(objetivos.get(id), fecha)).aDia()
                : DiaDelSemaforo.sinPorcentaje(fecha, calendario.estadoSinCalculo(fecha))));
        return resultado;
    }

    /** Qué días se miden de cada uno, con la misma fuente que el barrido. Sin fechas coherentes, no se mide. */
    private Map<UserId, CalendarioDeMedicion> calendariosDe(Collection<UserId> participantes) {
        if (participantes.isEmpty()) {
            return Map.of();
        }
        Map<UserId, ProgramaActivado> programas = new LinkedHashMap<>();
        programasFinder.deVarios(participantes).forEach((id, programa) -> {
            if (programa.primeraFecha() != null && programa.ultimaFecha() != null) {
                programas.put(id, programa);
            }
        });
        if (programas.isEmpty()) {
            return Map.of();
        }
        Map<UserId, List<PausaDeMedicion>> pausas = pausasPort.de(programas.keySet());
        Map<UserId, CalendarioDeMedicion> calendarios = new LinkedHashMap<>();
        programas.forEach((id, p) -> calendarios.put(id,
                new CalendarioDeMedicion(p.primeraFecha(), p.ultimaFecha(), pausas.get(id))));
        return calendarios;
    }

    private static ConteoDelDia delDia(List<ConteoDelDia> conteos, LocalDate fecha) {
        return conteos == null ? null
                : conteos.stream().filter(c -> c.fecha().equals(fecha)).findFirst().orElse(null);
    }
}
