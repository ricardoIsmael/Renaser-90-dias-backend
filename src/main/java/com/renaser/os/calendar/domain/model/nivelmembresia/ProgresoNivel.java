package com.renaser.os.calendar.domain.model.nivelmembresia;

import java.util.List;

/**
 * Puerto directo de {@code programProgressPercent}/{@code resolveLevelRank}
 * (audience.ts, repo viejo). DOMINIO PURO.
 */
public final class ProgresoNivel {

    private static final int DURACION_PROGRAMA_DIAS = 90;

    private ProgresoNivel() {
    }

    /** {@code Math.min(100, Math.round(programDay / 90 * 100))} — el repo viejo, literal. */
    public static int porcentajeDeProgreso(int diaPrograma) {
        double pct = Math.round(diaPrograma / (double) DURACION_PROGRAMA_DIAS * 100);
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
