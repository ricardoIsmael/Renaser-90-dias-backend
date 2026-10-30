package com.renaser.os.mentoring.domain.model.huecos;

import java.time.LocalDate;
import java.util.List;

/**
 * Los huecos de una semana de lunes a domingo de una persona: los siete días, los tramos que se repiten libres y lo
 * real frente a lo planificado (spec 015). Es lo que se calcula al pedir la semana en curso y lo que se guarda en la
 * foto al cerrarla.
 */
public record SemanaDeHuecos(LocalDate lunes, List<DiaDeHuecos> dias, List<HuecoQueSeRepite> repetidos, LoReal real) {

    public SemanaDeHuecos {
        if (dias.size() != 7) {
            throw new IllegalArgumentException("Una semana tiene siete dias: " + dias.size());
        }
        dias = List.copyOf(dias);
        repetidos = List.copyOf(repetidos);
    }

    /** Los huecos que se repiten salen de los días: se derivan una vez, acá. */
    public static SemanaDeHuecos de(LocalDate lunes, List<DiaDeHuecos> dias, LoReal real) {
        return new SemanaDeHuecos(lunes, dias, HuecosQueSeRepiten.de(dias), real);
    }

    public int minutosOcupados() {
        return dias.stream().mapToInt(DiaDeHuecos::minutosOcupados).sum();
    }

    public int minutosLibres() {
        return dias.stream().mapToInt(DiaDeHuecos::minutosLibres).sum();
    }
}
