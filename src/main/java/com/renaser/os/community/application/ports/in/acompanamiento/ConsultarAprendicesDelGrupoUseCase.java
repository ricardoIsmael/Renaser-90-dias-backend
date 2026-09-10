package com.renaser.os.community.application.ports.in.acompanamiento;

import com.renaser.os.shared.domain.UserId;

import java.util.List;
import java.util.UUID;

/**
 * Roster autorizado de un grupo. A diferencia de {@code ConsultarContextoAcompanamientoUseCase},
 * acá el {@code grupoId} lo elige el cliente, así que la relación vigente entre el actor y ese
 * grupo se comprueba SIEMPRE antes de devolver un solo nombre (contracts.md: "no confiar solo
 * en learnerUserId ni en pertenencia pasada").
 */
public interface ConsultarAprendicesDelGrupoUseCase {

    PaginaAprendices aprendices(ConsultaAprendices consulta);

    /**
     * @param cursor  id del último aprendiz de la página anterior, o {@code null} para la primera.
     * @param limite  tope técnico de la página, independiente del cupo comercial del grupo.
     */
    record ConsultaAprendices(UserId actorId, UUID grupoId, UUID cursor, int limite) {
    }

    record PaginaAprendices(UUID grupoId, String grupoNombre, String cobertura, int total,
                             List<AprendizDelGrupo> aprendices, UUID siguienteCursor) {
    }

    /**
     * Solo identidad. El avance (día de programa, hábitos, evidencias) llega por la consulta
     * semanal de seguimiento, que es una lectura distinta y más cara; mezclarlas obligaría a
     * un N+1 sobre habits y evidence para pintar una lista (plan.md §7).
     */
    record AprendizDelGrupo(UUID participanteId, String nombre, String avatarUrl, boolean activo) {
    }
}
