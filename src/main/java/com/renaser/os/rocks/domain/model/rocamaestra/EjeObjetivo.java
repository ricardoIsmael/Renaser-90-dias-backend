package com.renaser.os.rocks.domain.model.rocamaestra;

/**
 * Los 3 ejes de vida sobre los que se planifican las Rocas (Cuerpo, Trabajo,
 * Relaciones). Espejo del tipo Postgres `eje_objetivo` (antes texto libre
 * `GoalAxis` en el repo viejo: BODY/WORK/RELATIONSHIPS).
 */
public enum EjeObjetivo {
    CUERPO,
    TRABAJO,
    RELACIONES
}
