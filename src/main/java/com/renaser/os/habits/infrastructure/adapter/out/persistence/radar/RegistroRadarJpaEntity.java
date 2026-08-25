package com.renaser.os.habits.infrastructure.adapter.out.persistence.radar;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** Tabla `registros_radar` (baseline linea ~623) — sin `actualizado_en`: es append-only, nunca se edita. */
@Entity
@Table(name = "registros_radar", schema = "renaser")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegistroRadarJpaEntity {

    @Id
    private UUID id;

    private UUID participanteId;

    private String queHago;

    private String quePienso;

    private String queSiento;

    private Short nivelEnergia;

    private String queEvito;

    private Instant creadoEn;
}
