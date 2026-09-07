package com.renaser.os.habits.infrastructure.adapter.out.persistence.preferencia;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class HorarioSemanalPk implements Serializable {
    private UUID participanteId;
    private UUID habitoId;
    /** ISO-8601: 1 = lunes ... 7 = domingo, igual que `DayOfWeek.getValue()`. Ver V39. */
    private Short diaSemana;
}
