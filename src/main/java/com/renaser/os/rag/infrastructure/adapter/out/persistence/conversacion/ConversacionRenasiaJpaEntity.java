package com.renaser.os.rag.infrastructure.adapter.out.persistence.conversacion;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** 1:1 real con `usuarios`: {@code usuarioId} es a la vez PK y FK (docs/MODULO_RAG.md §2). */
@Entity
@Table(name = "conversaciones_renasia", schema = "renaser")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConversacionRenasiaJpaEntity {

    @Id
    private UUID usuarioId;

    private Instant creadoEn;

    private Instant actualizadoEn;
}
