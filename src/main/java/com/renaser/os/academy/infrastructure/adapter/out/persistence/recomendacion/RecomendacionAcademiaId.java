package com.renaser.os.academy.infrastructure.adapter.out.persistence.recomendacion;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.UUID;

/** Clase de PK compuesta para {@link RecomendacionAcademiaJpaEntity} (participante_id, fecha). */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecomendacionAcademiaId implements Serializable {

    private UUID participanteId;
    private LocalDate fecha;
}
