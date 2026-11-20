package com.renaser.os.rocks.application.ports.in.objetivos;

import com.renaser.os.rocks.api.ObjetivosDelAprendizFinder.ObjetivosDelAprendiz;
import com.renaser.os.shared.domain.UserId;

import java.util.Objects;
import java.util.UUID;

/**
 * Los objetivos de un alumno para quien lo acompaña hoy (spec 057, FR-001 a FR-003): el mentor o la guía VIGENTE de su grupo. Solo lectura.
 */
public interface ConsultarObjetivosDelAlumnoUseCase {

    /**
     * @throws com.renaser.os.shared.domain.NotAuthorizedException        (403) si no acompaña ese grupo hoy, el alumno ya no es de ese grupo o su cuenta está suspendida
     * @throws com.renaser.os.shared.domain.RechazoConCodigoException     {@code SIN_PROGRAMA_ACTIVO} (404) si el alumno no tiene programa activo o está suspendido
     */
    ObjetivosDelAprendiz objetivosDe(ConsultaObjetivos consulta);

    /** Los tres juntos porque los tres hacen falta para autorizar (el grupo solo no alcanza). */
    record ConsultaObjetivos(UserId actorId, UUID grupoId, UserId alumnoId) {

        public ConsultaObjetivos {
            Objects.requireNonNull(actorId, "actorId es obligatorio");
            Objects.requireNonNull(grupoId, "grupoId es obligatorio");
            Objects.requireNonNull(alumnoId, "alumnoId es obligatorio");
        }
    }
}
