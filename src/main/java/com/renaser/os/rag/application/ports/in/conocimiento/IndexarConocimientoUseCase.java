package com.renaser.os.rag.application.ports.in.conocimiento;

import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;
import java.util.UUID;

/**
 * D-46: la base de conocimiento la puebla un ADMIN/ALCHEMIST desde la aplicación, no un
 * proceso batch externo — la verificación de rol vive en {@code ConocimientoService}
 * (CLAUDE.MD sec. 5.4.6: el controller no decide reglas de negocio).
 */
public interface IndexarConocimientoUseCase {

    ChunkIndexado indexar(IndexarConocimientoCommand command);

    /**
     * {@code clase}, {@code documentoId} y {@code leccionId} son opcionales — una fuente
     * puede no venir de una lección puntual del programa (ver {@code base_conocimiento},
     * docs/MODULO_RAG.md sec. 2).
     */
    record IndexarConocimientoCommand(@NotNull UserId actorId, @NotBlank String tipoFuente, String clase,
                                       String documentoId, String leccionId, @NotBlank String contenido,
                                       Map<String, String> metadatos) {

        public IndexarConocimientoCommand {
            SelfValidating.validateConstructorArgs(IndexarConocimientoCommand.class, actorId, tipoFuente, clase,
                    documentoId, leccionId, contenido, metadatos);
            metadatos = metadatos == null ? Map.of() : Map.copyOf(metadatos);
        }
    }

    record ChunkIndexado(UUID id) {
    }
}
