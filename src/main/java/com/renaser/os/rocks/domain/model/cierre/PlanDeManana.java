package com.renaser.os.rocks.domain.model.cierre;

/**
 * «¿Qué harás mañana?» con una acción que quedó pendiente (spec 017, FR-002). Solo se registra: no mueve la acción de hora ni la
 * borra (fuera de alcance, ver la spec). El nombre de la constante es el valor guardado en {@code cierre_manana}.
 */
public enum PlanDeManana {
    /** «Hacerla a otra hora». */
    OTRA_HORA,
    /** «Hacerla más corta». */
    MAS_CORTA,
    /** «Pedir ayuda». */
    PEDIR_AYUDA,
    /** «Dejarla». */
    DEJARLA;

    public static PlanDeManana desde(String valor) {
        for (PlanDeManana plan : values()) {
            if (plan.name().equals(valor)) {
                return plan;
            }
        }
        throw new IllegalArgumentException("Elige qué harás mañana con la acción: ese plan no existe");
    }
}
