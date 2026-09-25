package com.renaser.os.mentoring.application.ports.in;

import com.renaser.os.points.api.DetalleDelSemaforo;
import com.renaser.os.shared.domain.UserId;

import java.util.Objects;
import java.util.UUID;

/**
 * El mismo detalle del semáforo de una persona que ve su mentor, leído por ADMIN/ALQUIMISTA sin
 * exigir relación (docs/arquitectura/SEMAFORO_DEL_APRENDIZ.md §4.1). Otra puerta y no un parámetro
 * del caso de uso del mentor, por el motivo que explica {@link ConsultarSemaforoDelGrupoAdministrativoUseCase}.
 *
 * <p>No lleva {@code grupoId}: el administrador mira a la persona, tenga o no grupo vigente.
 */
public interface ConsultarSemaforoDelAprendizAdministrativoUseCase {

    DetalleDelSemaforo detalleDe(ConsultaDetalleAdministrativa consulta);

    /** @param semanas se acota igual que en la consulta del mentor (1..13) */
    record ConsultaDetalleAdministrativa(UserId actorId, UUID aprendizId, int semanas) {

        public ConsultaDetalleAdministrativa {
            Objects.requireNonNull(actorId, "actorId es obligatorio");
            Objects.requireNonNull(aprendizId, "aprendizId es obligatorio");
            semanas = ConsultarSemaforoDelAprendizUseCase.acotarSemanas(semanas);
        }
    }
}
