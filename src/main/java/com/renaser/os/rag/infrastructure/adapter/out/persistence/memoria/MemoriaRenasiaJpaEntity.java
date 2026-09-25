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

/**
 * La fila de {@code memorias_renasia} (V67) de una persona: el resumen, que puede faltar, y hasta
 * donde se leyo la conversacion, que no retrocede nunca.
 */
@Entity
@Table(name = "memorias_renasia", schema = "renaser")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MemoriaRenasiaJpaEntity {

    @Id
    @Column(name = "participante_id")
    private UUID participanteId;

    @Column(name = "resumen")
    private String resumen;

    @Column(name = "compactado_hasta")
    private Instant compactadoHasta;

    @Column(name = "actualizado_en")
    private Instant actualizadoEn;
}
