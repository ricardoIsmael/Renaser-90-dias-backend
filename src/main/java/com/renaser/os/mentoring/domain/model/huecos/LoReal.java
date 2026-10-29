package com.renaser.os.mentoring.domain.model.huecos;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * Lo que de verdad pasó con lo planificado (FR-014): cuántos hábitos y acciones se cumplieron a tiempo, cuántos tarde y
 * cuántos no se cumplieron.
 *
 * <p>Un día que todavía no terminó no suma a «no cumplidos»: hoy sigue corriendo, y el futuro no existe. Un hábito
 * opcional sin cumplir tampoco es falta (mismo criterio que el semáforo). Un día sin ningún registro no se cuenta como
 * falta de nada: no hubo qué cumplir.
 */
public record LoReal(int aTiempo, int tarde, int noCumplidos) {

    public static final LoReal NADA = new LoReal(0, 0, 0);

    public static LoReal de(ZoneId zona, LocalDate hoy, List<Cumplible> cumplibles) {
        int aTiempo = 0;
        int tarde = 0;
        int noCumplidos = 0;
        for (Cumplible cumplible : cumplibles) {
            if (cumplible.fecha().isAfter(hoy)) {
                continue;
            }
            if (cumplible.cumplido()) {
                if (cumplible.aTiempo(zona)) {
                    aTiempo++;
                } else {
                    tarde++;
                }
            } else if (cumplible.fecha().isBefore(hoy) && !cumplible.opcional()) {
                noCumplidos++;
            }
        }
        return new LoReal(aTiempo, tarde, noCumplidos);
    }
}
