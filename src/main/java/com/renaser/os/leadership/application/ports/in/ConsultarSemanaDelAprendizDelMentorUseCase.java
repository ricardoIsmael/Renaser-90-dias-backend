package com.renaser.os.leadership.application.ports.in;

import com.renaser.os.leadership.application.ports.in.ConsultarSemanaDePersonaUseCase.SemanaDeLaPersona;
import com.renaser.os.shared.domain.UserId;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * La semana de un aprendiz, día por día y con sus hábitos por pilar, para SU MENTOR (spec 055, FR-011, FR-013 y FR-017). Es el
 * mismo armado de la ficha del líder ({@link ConsultarSemanaDePersonaUseCase}): cambia la puerta —acompañar hoy el
 * grupo— y la ventana, que también admite la semana en curso. <b>No deja registro de consulta</b>: ese registro es del
 * líder (spec 005, FR-016).
 *
 * <p>Vive en {@code leadership} y no en {@code mentoring} porque {@code leadership} ya depende de {@code mentoring.api};
 * al revés se cerraría un ciclo entre módulos que {@code ArchitectureTest} rechaza.
 */
public interface ConsultarSemanaDelAprendizDelMentorUseCase {

    /**
     * @throws com.renaser.os.shared.domain.NotAuthorizedException    {@code NO_ACOMPANA}
     * @throws com.renaser.os.shared.domain.RechazoConCodigoException {@code SEMANA_INVALIDA} o {@code FUERA_DEL_PROGRAMA}
     */
    SemanaDelAprendizParaElMentor semanaDe(ConsultaSemanaDelAprendiz consulta);

    /** @param semanaHasta el viernes que cierra la semana; null = la semana en curso del aprendiz */
    record ConsultaSemanaDelAprendiz(UserId actorId, UUID grupoId, UUID aprendizId, LocalDate semanaHasta) {

        public ConsultaSemanaDelAprendiz {
            Objects.requireNonNull(actorId, "actorId es obligatorio");
            Objects.requireNonNull(grupoId, "grupoId es obligatorio");
            Objects.requireNonNull(aprendizId, "aprendizId es obligatorio");
        }
    }

    /** @param diaUno el Día 1 de su programa, que limita el mes que el mentor puede recorrer; null si todavía no lo eligió */
    record SemanaDelAprendizParaElMentor(SemanaDeLaPersona semana, LocalDate diaUno) {
    }
}
