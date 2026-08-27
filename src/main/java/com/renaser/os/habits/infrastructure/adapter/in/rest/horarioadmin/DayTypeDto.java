package com.renaser.os.habits.infrastructure.adapter.in.rest.horarioadmin;

import com.renaser.os.habits.domain.model.habito.TipoDia;

public enum DayTypeDto {
    DISCIPLINE,
    INTOXICATION,
    ALL,
    SUNDAY;

    public static DayTypeDto from(TipoDia tipoDia) {
        return switch (tipoDia) {
            case DISCIPLINA -> DISCIPLINE;
            case INTOXICACION -> INTOXICATION;
            case TODOS -> ALL;
            case DOMINGO -> SUNDAY;
        };
    }

    public TipoDia toDomain() {
        return switch (this) {
            case DISCIPLINE -> TipoDia.DISCIPLINA;
            case INTOXICATION -> TipoDia.INTOXICACION;
            case ALL -> TipoDia.TODOS;
            case SUNDAY -> TipoDia.DOMINGO;
        };
    }
}
