package com.renaser.os.rag.infrastructure.adapter.out.persistence.conversacion;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

/** Clase de PK compuesta para FuenteMensajeRenasiaJpaEntity (mensaje_id, leccion_id). */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FuenteMensajeRenasiaId implements Serializable {

    private UUID mensajeId;
    private String leccionId;
}
