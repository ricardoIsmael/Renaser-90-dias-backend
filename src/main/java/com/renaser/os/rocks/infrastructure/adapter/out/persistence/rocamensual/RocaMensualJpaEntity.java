package com.renaser.os.rocks.infrastructure.adapter.out.persistence.rocamensual;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "rocas_mensuales", schema = "renaser")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RocaMensualJpaEntity {

    @Id
    private UUID id;

    private UUID rocaMaestraId;

    /** {@code smallint} en la base, igual que {@code numeroSemana} de la roca semanal. */
    private Short numeroMes;

    private String titulo;

    /** Meta / avance / unidad de V36. Las tres van juntas o las tres en null — lo impone el CHECK. */
    private BigDecimal meta;

    private BigDecimal avance;

    private String unidad;

    private Instant creadoEn;

    private Instant actualizadoEn;
}
