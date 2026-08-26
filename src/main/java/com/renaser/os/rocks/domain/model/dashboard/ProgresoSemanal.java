package com.renaser.os.rocks.domain.model.dashboard;

/**
 * {@code weekProgress} del dashboard (Hueco #15): completadas/planificadas ×
 * 100, redondeado, solo contando los días YA transcurridos de la semana
 * (denominador variable — una semana recién empezada no castiga los días que
 * todavía no llegaron). Portado de {@code rocks/service.ts:892-901}
 * (repo viejo). Ventana sin ningún día transcurrido con rocas -> 0, no 100
 * (a diferencia de {@code PorcentajeRocas} del D-43: acá 0 refleja "todavía no
 * hay nada que mostrar en esta semana", el criterio del repo viejo).
 */
public final class ProgresoSemanal {

    private ProgresoSemanal() {
    }

    public static int calcular(int totalPlanificado, int totalCompletado) {
        if (totalPlanificado <= 0) {
            return 0;
        }
        if (totalCompletado < 0 || totalCompletado > totalPlanificado) {
            throw new IllegalArgumentException(
                    "totalCompletado fuera de rango: " + totalCompletado + "/" + totalPlanificado);
        }
        return Math.toIntExact(Math.round(totalCompletado * 100.0 / totalPlanificado));
    }
}
