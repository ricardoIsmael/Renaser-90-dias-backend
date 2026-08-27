package com.renaser.os.habits.infrastructure.adapter.in.rest.habitoadmin;

import com.renaser.os.habits.domain.model.habito.TipoHabito;

/**
 * Espejo de {@code HabitType} del cliente (`habitsAdmin.ts`) — nombres en ingles, no los
 * del dominio en español. Mapeo a mano en la frontera web (CLAUDE.MD §5.4.1): RATING/BLOCKING
 * no coinciden textualmente con CALIFICACION/BLOQUEO, no es un simple {@code valueOf}.
 */
public enum HabitTypeDto {
    CHECKBOX,
    JOURNALING,
    RATING,
    BLOCKING;

    public static HabitTypeDto from(TipoHabito tipo) {
        return switch (tipo) {
            case CHECKBOX -> CHECKBOX;
            case JOURNALING -> JOURNALING;
            case CALIFICACION -> RATING;
            case BLOQUEO -> BLOCKING;
        };
    }

    public TipoHabito toDomain() {
        return switch (this) {
            case CHECKBOX -> TipoHabito.CHECKBOX;
            case JOURNALING -> TipoHabito.JOURNALING;
            case RATING -> TipoHabito.CALIFICACION;
            case BLOCKING -> TipoHabito.BLOQUEO;
        };
    }
}
