package com.renaser.os.habits.infrastructure.adapter.out.persistence.desbloqueo;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "desbloqueos_habito", schema = "renaser")
@IdClass(DesbloqueoHabitoPk.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DesbloqueoHabitoJpaEntity {

    @Id
    private UUID participanteId;

    @Id
    private UUID habitoId;

    private Short diaDesbloqueo;

    private Instant elegidoEn;

    private Instant creadoEn;

    private Instant actualizadoEn;

    /** `pausado_en` (V23): NULL = ACTIVO para este aprendiz. */
    private Instant pausadoEn;

    /** `pausado_hasta` (V31): ultimo dia INCLUSIVE de la pausa. NULL con pausa = indefinida. */
    private LocalDate pausadoHasta;
}
