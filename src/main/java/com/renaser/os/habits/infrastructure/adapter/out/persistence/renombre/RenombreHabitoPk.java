package com.renaser.os.habits.infrastructure.adapter.out.persistence.renombre;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class RenombreHabitoPk implements Serializable {

    private UUID participanteId;
    private UUID habitoId;

    public RenombreHabitoPk() {
    }

    public RenombreHabitoPk(UUID participanteId, UUID habitoId) {
        this.participanteId = participanteId;
        this.habitoId = habitoId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof RenombreHabitoPk that)) {
            return false;
        }
        return Objects.equals(participanteId, that.participanteId) && Objects.equals(habitoId, that.habitoId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(participanteId, habitoId);
    }
}
