package com.renaser.os.habits.infrastructure.adapter.out.persistence.eleccion;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public class EleccionDiaSemanalPk implements Serializable {

    private UUID participanteId;
    private UUID habitoId;
    private LocalDate fechaEjecucion;

    public EleccionDiaSemanalPk() {
    }

    public EleccionDiaSemanalPk(UUID participanteId, UUID habitoId, LocalDate fechaEjecucion) {
        this.participanteId = participanteId;
        this.habitoId = habitoId;
        this.fechaEjecucion = fechaEjecucion;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof EleccionDiaSemanalPk that)) {
            return false;
        }
        return Objects.equals(participanteId, that.participanteId) && Objects.equals(habitoId, that.habitoId)
                && Objects.equals(fechaEjecucion, that.fechaEjecucion);
    }

    @Override
    public int hashCode() {
        return Objects.hash(participanteId, habitoId, fechaEjecucion);
    }
}
