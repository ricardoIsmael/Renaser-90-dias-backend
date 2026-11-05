package com.renaser.os.habits.domain.model.carrera;

/**
 * Lo que decidió el servidor de una carrera (spec 042): si cuenta para el hábito (FR-024) y si queda «por revisar» (FR-025). Los
 * motivos los lee el mentor en la ficha y la persona en la pantalla «Terminaste».
 */
public record EvaluacionDeCarrera(boolean cuenta, String motivoNoCuenta, boolean porRevisar, String motivoRevision) {

    public EvaluacionDeCarrera {
        if (cuenta == (motivoNoCuenta != null)) {
            throw new IllegalArgumentException("Una carrera que no cuenta lleva su motivo, y una que cuenta no");
        }
        if (porRevisar != (motivoRevision != null)) {
            throw new IllegalArgumentException("Una carrera por revisar lleva su motivo, y una que no, no");
        }
    }

    public static EvaluacionDeCarrera queCuenta(String motivoRevision) {
        return new EvaluacionDeCarrera(true, null, motivoRevision != null, motivoRevision);
    }

    public static EvaluacionDeCarrera queNoCuenta(String motivo, String motivoRevision) {
        return new EvaluacionDeCarrera(false, motivo, motivoRevision != null, motivoRevision);
    }
}
