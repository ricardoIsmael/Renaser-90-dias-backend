package com.renaser.os.rag.infrastructure.adapter.out.persistence.memoria;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** Una fila de {@code recuerdos_renasia} (V67): algo que el acompanante aprendio de la persona. */
@Entity
@Table(name = "recuerdos_renasia", schema = "renaser")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecuerdoRenasiaJpaEntity {

    @Id
    private UUID id;

    @Column(name = "participante_id")
    private UUID participanteId;

    /** El nombre de {@code CategoriaDeRecuerdo}; el CHECK de V67 solo acepta esos tres. */
    @Column(name = "categoria")
    private String categoria;

    @Column(name = "texto")
    private String texto;

    @Column(name = "creado_en")
    private Instant creadoEn;
}
