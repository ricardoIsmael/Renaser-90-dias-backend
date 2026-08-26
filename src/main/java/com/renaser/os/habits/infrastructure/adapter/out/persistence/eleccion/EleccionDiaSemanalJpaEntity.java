package com.renaser.os.habits.infrastructure.adapter.out.persistence.eleccion;

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
@Table(name = "dias_semanales_habito", schema = "renaser")
@IdClass(EleccionDiaSemanalPk.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EleccionDiaSemanalJpaEntity {

    @Id
    private UUID participanteId;

    @Id
    private UUID habitoId;

    @Id
    private LocalDate fechaEjecucion;

    private LocalDate semanaInicio;

    private Instant creadoEn;
}
