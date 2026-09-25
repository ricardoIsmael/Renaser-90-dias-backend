package com.renaser.os.rag.domain.model.agenda;

import java.time.DayOfWeek;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Las horas en que la persona suele estar ocupada cada dia de la semana (D-161): lo que el
 * acompanante usa para sugerirle cuando hacer sus habitos sin volver a preguntarlo cada vez.
 *
 * <p>Guarda lo minimo: dia de la semana y tramos, sin etiqueta ("trabajo", "clases"). Se cambia por
 * dias enteros ({@link #conDias}): decir "de lunes a viernes 09:00-18:00" reemplaza lo que habia
 * esos dias y deja los demas como estaban. Un tramo nocturno pasa su madrugada al dia siguiente.
 */
public record AgendaSemanal(Map<DayOfWeek, AgendaOcupada> dias) {

    public AgendaSemanal {
        EnumMap<DayOfWeek, AgendaOcupada> copia = new EnumMap<>(DayOfWeek.class);
        Objects.requireNonNull(dias, "dias es obligatorio").forEach((dia, agenda) -> {
            if (!agenda.estaLibre()) {
                copia.put(dia, agenda);
            }
        });
        dias = Map.copyOf(copia);
    }

    public static AgendaSemanal vacia() {
        return new AgendaSemanal(Map.of());
    }

    public AgendaOcupada delDia(DayOfWeek dia) {
        return dias.getOrDefault(dia, AgendaOcupada.libre());
    }

    public boolean estaVacia() {
        return dias.isEmpty();
    }

    /**
     * Reemplaza esos dias con los tramos leidos de {@code ocupado} ("ninguno" los deja libres). La
     * madrugada de un tramo nocturno se suma al dia siguiente, sin borrar lo que ese dia ya tenia.
     */
    public AgendaSemanal conDias(Set<DayOfWeek> cuales, String ocupado) {
        EnumMap<DayOfWeek, AgendaOcupada> nueva = new EnumMap<>(DayOfWeek.class);
        nueva.putAll(dias);
        if (esNinguno(ocupado)) {
            cuales.forEach(nueva::remove);
            return new AgendaSemanal(nueva);
        }
        AgendaOcupada.Lectura lectura = AgendaOcupada.leerConMedianoche(ocupado);
        cuales.forEach(dia -> nueva.put(dia, lectura.delDia()));
        if (!lectura.delDiaSiguiente().estaLibre()) {
            cuales.forEach(dia -> nueva.merge(dia.plus(1), lectura.delDiaSiguiente(), AgendaOcupada::mas));
        }
        return new AgendaSemanal(nueva);
    }

    /** "lunes 09:00-18:00; martes 09:00-18:00", o "no tiene horas ocupadas guardadas". */
    public String texto() {
        if (dias.isEmpty()) {
            return "no tiene horas ocupadas guardadas";
        }
        return new EnumMap<>(dias).entrySet().stream()
                .map(dia -> DiasDeSemana.nombre(dia.getKey()) + " " + dia.getValue().texto())
                .collect(Collectors.joining("; "));
    }

    private static boolean esNinguno(String ocupado) {
        return ocupado != null && ocupado.strip().equalsIgnoreCase("ninguno");
    }
}
