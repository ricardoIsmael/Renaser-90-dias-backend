package com.renaser.os.points.application.services;

import com.renaser.os.points.application.ports.out.semaforo.CargarDiasDelSemaforoPort;
import com.renaser.os.points.application.ports.out.semaforo.PausasDelSemaforoPort;
import com.renaser.os.points.domain.model.semaforo.CalendarioDeMedicion;
import com.renaser.os.points.domain.model.semaforo.MedicionDeLaPersona;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.ProgramasActivadosFinder;
import com.renaser.os.users.api.ProgramasActivadosFinder.ProgramaActivado;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * Carga en lote lo que se sabe del semáforo de varias personas: su programa (qué días se miden), sus
 * pausas y los días ya guardados del rango que pide quien llama. Tres consultas para toda la
 * colección, nunca una por persona. Cada persona queda con SU "hoy" local (regla 02 §1).
 */
@Component
class LecturaDeMediciones {

    private final ProgramasActivadosFinder programasFinder;
    private final CargarDiasDelSemaforoPort diasPort;
    private final PausasDelSemaforoPort pausasPort;
    private final Clock clock;

    LecturaDeMediciones(ProgramasActivadosFinder programasFinder, CargarDiasDelSemaforoPort diasPort,
                        PausasDelSemaforoPort pausasPort, Clock clock) {
        this.programasFinder = programasFinder;
        this.diasPort = diasPort;
        this.pausasPort = pausasPort;
        this.clock = clock;
    }

    /** Lo que se sabe de una persona medida, y en qué zona vive. */
    record Lectura(ZoneId zona, MedicionDeLaPersona medicion) {
    }

    /** Solo la ventana vigente: los siete días cerrados que terminan ayer. */
    Map<UserId, Lectura> vigenteDe(Collection<UserId> participantes) {
        return de(participantes, MedicionDeLaPersona::desdeVigente, hoy -> hoy.minusDays(1));
    }

    /**
     * @param desdeSegunHoy primer día a leer según el "hoy" de cada persona
     * @param hastaSegunHoy último día a leer según el "hoy" de cada persona
     * @return sin clave = esa persona no se mide (sin programa activado)
     */
    Map<UserId, Lectura> de(Collection<UserId> participantes, UnaryOperator<LocalDate> desdeSegunHoy,
                            UnaryOperator<LocalDate> hastaSegunHoy) {
        Map<UserId, ProgramaActivado> programas = programasMedibles(participantes);
        if (programas.isEmpty()) {
            return Map.of();
        }
        Instant ahora = clock.now();
        List<LocalDate> hoys = programas.values().stream().map(p -> hoyDe(p, ahora)).toList();
        LocalDate desde = hoys.stream().map(desdeSegunHoy).min(LocalDate::compareTo).orElseThrow();
        LocalDate hasta = hoys.stream().map(hastaSegunHoy).max(LocalDate::compareTo).orElseThrow();
        var filas = diasPort.entre(programas.keySet(), desde, hasta);
        var pausas = pausasPort.de(programas.keySet());
        Map<UserId, Lectura> lecturas = new LinkedHashMap<>();
        programas.forEach((id, programa) -> lecturas.put(id, new Lectura(programa.zona(), new MedicionDeLaPersona(
                new CalendarioDeMedicion(programa.primeraFecha(), programa.ultimaFecha(), pausas.get(id)),
                filas.get(id), hoyDe(programa, ahora)))));
        return lecturas;
    }

    private Map<UserId, ProgramaActivado> programasMedibles(Collection<UserId> participantes) {
        if (participantes.isEmpty()) {
            return Map.of();
        }
        Map<UserId, ProgramaActivado> medibles = new LinkedHashMap<>();
        programasFinder.deVarios(participantes).forEach((id, programa) -> {
            // Una fila activada sin fechas es un dato incoherente: no se inventa un calendario.
            if (programa.primeraFecha() != null && programa.ultimaFecha() != null) {
                medibles.put(id, programa);
            }
        });
        return medibles;
    }

    private static LocalDate hoyDe(ProgramaActivado programa, Instant ahora) {
        return ahora.atZone(programa.zona()).toLocalDate();
    }
}
