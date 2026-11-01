package com.renaser.os.calendar.domain.model.nivelmembresia;

import com.renaser.os.shared.domain.DuracionDelPrograma;

import java.util.List;

/**
 * Puerto directo de {@code programProgressPercent}/{@code resolveLevelRank}
 * (audience.ts, repo viejo). DOMINIO PURO.
 */
public final class ProgresoNivel {

    private ProgresoNivel() {
    }

    /**
     * {@code Math.min(100, Math.round(programDay / duracion * 100))}. El repo viejo dividía siempre por 90; desde las formaciones
     * (spec 022/023) se divide por la duración del programa de la persona (90 o 365): el día 45 de un Diplomado de 1 año es el 12 %,
     * no el 50 %. Una duración que no es positiva (dato roto) cuenta como la de siempre.
     */
    public static int porcentajeDeProgreso(int diaPrograma, int duracionDias) {
        int duracion = duracionDias > 0 ? duracionDias : DuracionDelPrograma.POR_DEFECTO;
        double pct = Math.round(diaPrograma / (double) duracion * 100);
        return (int) Math.min(100, pct);
    }

    /** El rango mas alto cuyo pctProgresoMinimo <= porcentaje. 0 si ninguno califica. */
    public static int resolverRango(int porcentaje, List<NivelMembresia> niveles) {
        int mejor = 0;
        for (NivelMembresia nivel : niveles) {
            if (nivel.pctProgresoMinimo() <= porcentaje && nivel.rango() > mejor) {
                mejor = nivel.rango();
            }
        }
        return mejor;
    }
}
