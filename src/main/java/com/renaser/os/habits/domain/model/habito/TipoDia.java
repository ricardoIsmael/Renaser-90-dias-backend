package com.renaser.os.habits.domain.model.habito;

/** Espejo de `tipo_dia` (baseline SQL). DISCIPLINA/INTOXICACION derivan del dia
 * de programa (ciclos fijos); TODOS aplica cualquier dia; DOMINGO es especial. */
public enum TipoDia {
    DISCIPLINA,
    INTOXICACION,
    TODOS,
    DOMINGO
}
