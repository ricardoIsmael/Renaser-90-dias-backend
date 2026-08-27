package com.renaser.os.habits.infrastructure.adapter.in.rest.guiaadmin;

import com.renaser.os.habits.domain.model.guia.SeccionGuia;

/** Espejo de {@code HabitGuideSection} (`habitsAdmin.ts`). */
public enum HabitGuideSectionDto {
    WHAT_TO_DO,
    HOW_TO_DO,
    SCIENCE,
    RENASER,
    ALCHEMY,
    OUTCOMES,
    HOW_TO_VALIDATE;

    public static HabitGuideSectionDto from(SeccionGuia seccion) {
        return switch (seccion) {
            case QUE_HACER -> WHAT_TO_DO;
            case COMO_HACERLO -> HOW_TO_DO;
            case CIENCIA -> SCIENCE;
            case RENASER -> RENASER;
            case ALQUIMIA -> ALCHEMY;
            case RESULTADOS -> OUTCOMES;
            case COMO_VALIDAR -> HOW_TO_VALIDATE;
        };
    }

    public SeccionGuia toDomain() {
        return switch (this) {
            case WHAT_TO_DO -> SeccionGuia.QUE_HACER;
            case HOW_TO_DO -> SeccionGuia.COMO_HACERLO;
            case SCIENCE -> SeccionGuia.CIENCIA;
            case RENASER -> SeccionGuia.RENASER;
            case ALCHEMY -> SeccionGuia.ALQUIMIA;
            case OUTCOMES -> SeccionGuia.RESULTADOS;
            case HOW_TO_VALIDATE -> SeccionGuia.COMO_VALIDAR;
        };
    }
}
