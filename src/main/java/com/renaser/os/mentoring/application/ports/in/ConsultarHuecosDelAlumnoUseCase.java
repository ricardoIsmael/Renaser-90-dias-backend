package com.renaser.os.mentoring.application.ports.in;

import com.renaser.os.mentoring.api.HuecosDeLaSemana;
import com.renaser.os.shared.domain.UserId;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Los huecos de horario de un alumno, para quien lo acompaña (spec 015): el mentor o la guía vigente de SU grupo. Solo
 * lectura: la persona no los ve en esta primera versión.
 */
public interface ConsultarHuecosDelAlumnoUseCase {

    HuecosDeLaSemana semanaDe(ConsultaHuecos consulta);

    /**
     * Quién pregunta, por qué grupo y por cuál de sus alumnos: los tres juntos porque los tres hacen falta para
     * autorizar (el grupo solo no alcanza: un mentor legítimo leería a cualquiera con el id de SU grupo).
     *
     * @param semana el lunes de la semana pedida; {@code null} = la semana en curso del alumno, en SU zona
     */
    record ConsultaHuecos(UserId actorId, UUID grupoId, UserId alumnoId, LocalDate semana) {

        public ConsultaHuecos {
            Objects.requireNonNull(actorId, "actorId es obligatorio");
            Objects.requireNonNull(grupoId, "grupoId es obligatorio");
            Objects.requireNonNull(alumnoId, "alumnoId es obligatorio");
        }
    }
}
