package com.renaser.os.mentoring.application.ports.in;

import com.renaser.os.mentoring.application.ports.in.ConsultarSemaforoDelGrupoUseCase.TablaDelSemaforo;
import com.renaser.os.points.api.SemanaDelSemaforo;
import com.renaser.os.shared.domain.UserId;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * La misma tabla del semáforo que ve el mentor, leída por ADMIN/ALQUIMISTA sobre CUALQUIER grupo,
 * con nombres (docs/arquitectura/SEMAFORO_DEL_APRENDIZ.md §1.1 y §4.3).
 *
 * <p><b>Por qué es otra puerta y no un parámetro de {@link ConsultarSemaforoDelGrupoUseCase}.</b> Por
 * lo mismo que {@link ConsultarSemanaAdministrativaUseCase}: la autorización acá es de ROL, no de
 * relación, y darle una salida al guard del mentor es lo único que separaría a un exmentor de los
 * datos de su antiguo grupo. Lo que sí se comparte es el armado de la tabla: si fueran dos cálculos,
 * el administrador y el mentor verían números distintos del mismo día.
 */
public interface ConsultarSemaforoDelGrupoAdministrativoUseCase {

    TablaDelSemaforo tablaDe(ConsultaTablaAdministrativa consulta);

    /** @param semanaHasta igual que en la consulta del mentor: null = vigente; si viene, un viernes. */
    record ConsultaTablaAdministrativa(UserId actorId, UUID grupoId, LocalDate semanaHasta) {

        public ConsultaTablaAdministrativa {
            Objects.requireNonNull(actorId, "actorId es obligatorio");
            Objects.requireNonNull(grupoId, "grupoId es obligatorio");
            if (semanaHasta != null) {
                SemanaDelSemaforo.exigirCierreValido(semanaHasta);
            }
        }
    }
}
