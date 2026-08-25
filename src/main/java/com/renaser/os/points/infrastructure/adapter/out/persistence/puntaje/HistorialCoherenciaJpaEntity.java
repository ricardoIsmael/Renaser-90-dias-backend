package com.renaser.os.points.infrastructure.adapter.out.persistence.puntaje;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "historial_coherencia", schema = "renaser")
@IdClass(HistorialCoherenciaId.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class HistorialCoherenciaJpaEntity {

    @Id
    private UUID participanteId;

    @Id
    private LocalDate fecha;

    private BigDecimal valor;
}
