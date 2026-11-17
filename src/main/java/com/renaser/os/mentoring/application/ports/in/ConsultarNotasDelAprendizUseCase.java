package com.renaser.os.mentoring.application.ports.in;

import com.renaser.os.mentoring.api.NotaDelAprendiz;
import com.renaser.os.shared.domain.UserId;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Las notas privadas de un aprendiz, de la más nueva a la más vieja (spec 055, FR-024): para el mentor que lo acompaña hoy (todas,
 * también las de mentores anteriores) y, por la otra puerta, para Administración.
 */
public interface ConsultarNotasDelAprendizUseCase {

    /** @throws com.renaser.os.shared.domain.NotAuthorizedException {@code NO_ACOMPANA} si no acompaña hoy ese grupo o al aprendiz */
    List<NotaDelAprendiz> notasDelMentor(ConsultaNotasDelAprendiz consulta);

    /**
     * Administración (ADMIN y ALCHEMIST activos) lee las de cualquier aprendiz, con {@code puedeCorregir = false}.
     *
     * @throws java.util.NoSuchElementException si el aprendiz no existe (404)
     */
    List<NotaDelAprendiz> notasParaAdministracion(UserId actorId, UUID aprendizId);

    record ConsultaNotasDelAprendiz(UserId actorId, UUID grupoId, UUID aprendizId) {

        public ConsultaNotasDelAprendiz {
            Objects.requireNonNull(actorId, "actorId es obligatorio");
            Objects.requireNonNull(grupoId, "grupoId es obligatorio");
            Objects.requireNonNull(aprendizId, "aprendizId es obligatorio");
        }
    }
}
