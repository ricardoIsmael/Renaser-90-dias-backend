package com.renaser.os.habits.domain.model.radar;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Mi Radar de una persona (spec 012, rehecha por la 019): si lo encendió y, por día de la semana, cómo lo quiere. Las
 * HORAS ya no se guardan: las calcula {@link HorarioDelRadar} con el Despertar y el Dormir de cada día.
 *
 * <p>{@code dias} guarda solo los días que no son los de fábrica (Automático y con Radar), ordenados y sin repetir: un día
 * que no está es {@link DiaDeMiRadar#normal}. Apagar no borra los días: encender de nuevo los encuentra como estaban.
 * Valor inmutable.
 */
public record MiRadar(boolean encendido, List<DiaDeMiRadar> dias) {

    public MiRadar {
        if (dias == null) {
            throw new IllegalArgumentException("Los días de Mi Radar son obligatorios");
        }
        if (dias.stream().anyMatch(propio -> propio == null)) {
            throw new IllegalArgumentException("Cada día de Mi Radar necesita su número y su modo");
        }
        if (dias.stream().map(DiaDeMiRadar::dia).distinct().count() != dias.size()) {
            throw new IllegalArgumentException("No repitas el mismo día");
        }
        dias = dias.stream().filter(dia -> !dia.esNormal()).sorted(Comparator.comparingInt(DiaDeMiRadar::dia)).toList();
    }

    /** Apagado y con todos los días como vienen de fábrica. */
    public static MiRadar apagado() {
        return new MiRadar(false, List.of());
    }

    public MiRadar conEncendido(boolean nuevo) {
        return new MiRadar(nuevo, dias);
    }

    /** Cómo está ese día de la semana: lo guardado o, si no hay nada, el día de fábrica. */
    public DiaDeMiRadar dia(int dia) {
        return dias.stream().filter(propio -> propio.dia() == dia).findFirst().orElseGet(() -> DiaDeMiRadar.normal(dia));
    }

    public DiaDeMiRadar dia(DayOfWeek diaDeLaSemana) {
        return dia(diaDeLaSemana.getValue());
    }

    /** Lo mismo con estos días cambiados; los que ya estaban y no llegan se quedan como estaban. */
    public MiRadar conDias(List<DiaDeMiRadar> cambiados) {
        List<DiaDeMiRadar> resultado = new ArrayList<>(dias.stream()
                .filter(propio -> cambiados.stream().noneMatch(cambio -> cambio.dia() == propio.dia())).toList());
        resultado.addAll(cambiados);
        return new MiRadar(encendido, resultado);
    }
}
