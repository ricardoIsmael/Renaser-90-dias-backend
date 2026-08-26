package com.renaser.os.rag.infrastructure.adapter.out.persistence.conversacion;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** N:M mensaje-leccion: las fuentes citadas de cada respuesta del asistente
 * (`leccion_id` es `text`, ids estilo Skool — docs/MODULO_RAG.md §2). */
@Entity
@Table(name = "fuentes_mensaje_renasia", schema = "renaser")
@IdClass(FuenteMensajeRenasiaId.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FuenteMensajeRenasiaJpaEntity {

    @Id
    private UUID mensajeId;

    @Id
    private String leccionId;
}
