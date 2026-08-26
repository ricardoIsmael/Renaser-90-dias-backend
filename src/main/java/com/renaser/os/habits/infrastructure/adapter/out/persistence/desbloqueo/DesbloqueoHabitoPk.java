package com.renaser.os.habits.infrastructure.adapter.out.persistence.desbloqueo;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class DesbloqueoHabitoPk implements Serializable {

    private UUID participanteId;
    private UUID habitoId;

    public DesbloqueoHabitoPk() {
    }

    public DesbloqueoHabitoPk(UUID participanteId, UUID habitoId) {
        this.participanteId = participanteId;
        this.habitoId = habitoId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DesbloqueoHabitoPk that)) {
            return false;
        }
        return Objects.equals(participanteId, that.participanteId) && Objects.equals(habitoId, that.habitoId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(participanteId, habitoId);
    }
}
