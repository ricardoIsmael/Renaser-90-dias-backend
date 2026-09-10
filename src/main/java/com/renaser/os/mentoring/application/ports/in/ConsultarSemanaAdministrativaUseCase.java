package com.renaser.os.mentoring.application.ports.in;

import com.renaser.os.mentoring.application.ports.in.ConsultarSeguimientoSemanalUseCase.SemanaDelAlumno;
import com.renaser.os.shared.domain.UserId;

import java.time.LocalDate;
import java.util.UUID;

/**
 * La misma semana que ve el mentor, leida por ADMIN/ALQUIMISTA sobre CUALQUIER aprendiz.
 *
 * <p><b>Por que es un caso de uso aparte y no un parametro del otro.</b> La tentacion era agregarle
 * un {@code soyAdmin} a {@link ConsultarSeguimientoSemanalUseCase} o relajar su guard cuando el rol
 * lo permite. Las dos cosas terminan igual: el guard que protege la relacion mentor-alumno pasa a
 * tener una salida, y esa salida es lo unico que separa a un exmentor de los datos de su antiguo
 * grupo. Aca la autorizacion es OTRA —rol administrativo, sin relacion— y por eso vive en otra
 * puerta. Lo que si se comparte es el armado de la semana: obligaciones historicas, entregas y
 * motor de cumplimiento son los mismos, porque si fueran dos calculos distintos el administrador y
 * el mentor verian numeros distintos del mismo dia (SDD 003, ARF-08 / V22).
 *
 * <p>No hay {@code grupoId} en la consulta: el administrador mira a la persona, no al grupo, y un
 * aprendiz sin grupo vigente igual tiene semana. El grupo viaja en la respuesta cuando lo hay.
 */
public interface ConsultarSemanaAdministrativaUseCase {

    SemanaDelAlumno semanaDe(ConsultaSemanaAdministrativa consulta);

    /** @param inicioDeSemana {@code null} = la semana en curso, resuelta en la zona DEL ALUMNO. */
    record ConsultaSemanaAdministrativa(UserId actorId, UUID alumnoId, LocalDate inicioDeSemana) {
    }
}
