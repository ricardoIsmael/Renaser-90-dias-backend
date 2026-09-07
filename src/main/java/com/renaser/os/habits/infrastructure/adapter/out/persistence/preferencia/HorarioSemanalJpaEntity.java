package com.renaser.os.habits.infrastructure.adapter.out.persistence.preferencia;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "horario_semanal_habito", schema = "renaser")
@IdClass(HorarioSemanalPk.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class HorarioSemanalJpaEntity {

    @Id
    private UUID participanteId;

    @Id
    private UUID habitoId;

    /** ISO-8601: 1 = lunes ... 7 = domingo. Ver V39. */
    @Id
    private Short diaSemana;

    private LocalTime horaDisparo;

    /** V40: false = ese dia el habito NO va, ninguna semana. */
    private boolean activo;

    private LocalTime horaLimite;

    private Instant creadoEn;

    private Instant actualizadoEn;
}
