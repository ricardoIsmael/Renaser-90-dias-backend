package com.renaser.os.rocks.infrastructure.adapter.out.persistence.rocasemanal;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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

    /** Tabla hija `acciones_criticas` (1FN restaurada, baseline P-10) — sin @Entity propio: es parte del agregado. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "acciones_criticas", schema = "renaser", joinColumns = @JoinColumn(name = "roca_semanal_id"))
    private List<AccionCriticaEmbeddable> acciones = new ArrayList<>();

    private String obstaculo;

    private String contingencia;

    private Short autoevaluacionInicio;

    private Short autoevaluacionFin;

    private String bloqueoPrincipal;

    private String correccion;

    private Instant creadoEn;

    private Instant actualizadoEn;
}
