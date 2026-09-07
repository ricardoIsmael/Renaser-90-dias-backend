package com.renaser.os.habits.infrastructure.adapter.out.persistence.preferencia;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class HorarioPorFechaPk implements Serializable {
    private UUID participanteId;
    private UUID habitoId;
    private LocalDate fecha;
}
