package com.renaser.os.mentoring.application.ports.in;

import com.renaser.os.mentoring.api.NotaDelAprendiz;
import com.renaser.os.shared.domain.UserId;

import java.util.Objects;
import java.util.UUID;

/** El autor corrige su nota, solo el mismo día en que la escribió (spec 055, FR-025). */
public interface CorregirNotaDelAprendizUseCase {

    /**
     * @throws com.renaser.os.shared.domain.NotAuthorizedException    {@code NO_ACOMPANA} o {@code NO_ES_EL_AUTOR}
     * @throws com.renaser.os.shared.domain.RechazoConCodigoException {@code YA_NO_SE_PUEDE_CORREGIR} (409) o {@code NOTA_INVALIDA} (400)
     * @throws java.util.NoSuchElementException                       si la nota no es de este aprendiz (404)
     */
    NotaDelAprendiz corregir(CorregirNota pedido);

    record CorregirNota(UserId actorId, UUID grupoId, UUID aprendizId, UUID notaId, String texto) {

        public CorregirNota {
            Objects.requireNonNull(actorId, "actorId es obligatorio");
            Objects.requireNonNull(grupoId, "grupoId es obligatorio");
            Objects.requireNonNull(aprendizId, "aprendizId es obligatorio");
            Objects.requireNonNull(notaId, "notaId es obligatorio");
        }
    }
}
