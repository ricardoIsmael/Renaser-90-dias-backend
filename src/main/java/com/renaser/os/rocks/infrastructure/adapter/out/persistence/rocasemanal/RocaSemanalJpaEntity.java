package com.renaser.os.rocks.infrastructure.adapter.out.persistence.rocasemanal;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "rocas_semanales", schema = "renaser")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RocaSemanalJpaEntity {

    @Id
    private UUID id;

    private UUID rocaMaestraId;

    private Short numeroSemana;

    private String titulo;

    private String obstaculo;

    private String contingencia;

    private Short autoevaluacionInicio;

    private Short autoevaluacionFin;

    private String bloqueoPrincipal;

    private String correccion;

    private Instant creadoEn;

    private Instant actualizadoEn;
}
