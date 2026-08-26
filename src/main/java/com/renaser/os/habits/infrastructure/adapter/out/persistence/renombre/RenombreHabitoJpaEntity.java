package com.renaser.os.habits.infrastructure.adapter.out.persistence.renombre;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "renombres_habito", schema = "renaser")
@IdClass(RenombreHabitoPk.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RenombreHabitoJpaEntity {

    @Id
    private UUID participanteId;

    @Id
    private UUID habitoId;

    private String tituloPersonal;

    private String motivo;

    private Instant creadoEn;

    private Instant actualizadoEn;
}
