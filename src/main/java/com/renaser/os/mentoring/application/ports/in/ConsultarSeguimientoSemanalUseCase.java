package com.renaser.os.mentoring.application.ports.in;

import com.renaser.os.mentoring.api.SemanaDelAlumno;
import com.renaser.os.shared.domain.UserId;

import java.time.LocalDate;
import java.util.UUID;

/**
 * La semana de un alumno, vista por quien lo acompaña. La respuesta ({@link SemanaDelAlumno} y su familia) vive en
 * {@code mentoring.api} desde la spec 005, porque la ficha del líder la lee por {@code SemanaDeSeguimientoFinder}.
 */
public interface ConsultarSeguimientoSemanalUseCase {

    SemanaDelAlumno semanaDe(ConsultaSemana consulta);

    record ConsultaSemana(UserId actorId, UUID grupoId, UUID alumnoId, LocalDate inicioDeSemana) {
    }
}
