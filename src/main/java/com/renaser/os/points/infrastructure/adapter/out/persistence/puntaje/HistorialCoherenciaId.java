package com.renaser.os.points.infrastructure.adapter.out.persistence.puntaje;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.UUID;

/** Clase de PK compuesta para HistorialCoherenciaJpaEntity (participante_id, fecha). */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class HistorialCoherenciaId implements Serializable {

    private UUID participanteId;
    private LocalDate fecha;
}
