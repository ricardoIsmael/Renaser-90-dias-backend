package com.renaser.os.mentoring.application.ports.in;

import com.renaser.os.mentoring.api.NotaDelAprendiz;
import com.renaser.os.shared.domain.UserId;

import java.util.Objects;
import java.util.UUID;

/** El mentor que acompaña hoy a un aprendiz escribe una nota privada sobre él (spec 055, FR-023 y FR-026). */
public interface EscribirNotaDelAprendizUseCase {

    /**
     * @throws com.renaser.os.shared.domain.NotAuthorizedException    {@code NO_ACOMPANA}
     * @throws com.renaser.os.shared.domain.RechazoConCodigoException {@code NOTA_INVALIDA} si está vacía o pasa de 2000 caracteres
     */
    NotaDelAprendiz escribir(EscribirNota pedido);

    record EscribirNota(UserId actorId, UUID grupoId, UUID aprendizId, String texto) {

        public EscribirNota {
            Objects.requireNonNull(actorId, "actorId es obligatorio");
            Objects.requireNonNull(grupoId, "grupoId es obligatorio");
            Objects.requireNonNull(aprendizId, "aprendizId es obligatorio");
        }
    }
}
