package com.renaser.os.rocks.application.ports.in.cierre;

import com.renaser.os.rocks.api.CierresDelDiaFinder.CierresDeLaSemana;
import com.renaser.os.shared.domain.UserId;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Los cierres de una semana de un alumno, para quien lo acompaña (spec 017): el mentor o la guía VIGENTE de su grupo. Solo lectura.
 */
public interface ConsultarCierresDelAlumnoUseCase {

    CierresDeLaSemana semanaDe(ConsultaCierres consulta);

    /**
     * Quién pregunta, por qué grupo y por cuál de sus alumnos: los tres juntos porque los tres hacen falta para autorizar (el grupo
     * solo no alcanza: un mentor legítimo leería a cualquiera con el id de SU grupo).
     *
     * @param semana el lunes de la semana pedida; {@code null} = la semana en curso del alumno, en SU zona
     */
    record ConsultaCierres(UserId actorId, UUID grupoId, UserId alumnoId, LocalDate semana) {

        public ConsultaCierres {
            Objects.requireNonNull(actorId, "actorId es obligatorio");
            Objects.requireNonNull(grupoId, "grupoId es obligatorio");
            Objects.requireNonNull(alumnoId, "alumnoId es obligatorio");
        }
    }
}
