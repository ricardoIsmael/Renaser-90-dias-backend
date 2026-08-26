package com.renaser.os.evidence.application.ports.in.evidencia;

import com.renaser.os.evidence.api.EstadoValidacion;
import com.renaser.os.evidence.domain.model.evidencia.Evidencia;
import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Listado de evidencia (hueco #19). Autorización según rol del actor — CLAUDE.MD §0.3,
 * "un listado tiene que estar correctamente autorizado":
 *
 * <ul>
 *   <li>ADMIN/ALCHEMIST: cualquier evidencia, {@code participanteId} opcional.</li>
 *   <li>MENTOR: {@code participanteId} es OBLIGATORIO (no hay forma pública de listar
 *       "todos mis aprendices" en este alcance — ver docs/MODULO_EVIDENCE.md) y debe ser
 *       el mentor asignado a ese aprendiz, según {@code users.api.ParticipacionProgramaFinder}
 *       (mismo puerto y mismo criterio que {@code support.TicketMentorService.requireMentorAsignado}).</li>
 *   <li>Cualquier otro rol (TRAINEE, MENTOR_LEAD): solo la propia — {@code participanteId}
 *       nulo o igual a {@code actorId}; cualquier otro valor es 403.</li>
 * </ul>
 */
public interface ListarEvidenciaUseCase {

    PaginaEvidencias listar(ListarEvidenciaComando comando);

    /** "Tipo de entidad relacionada" — el filtro que pide el encargo, sin exponer el id
     * puntual (eso ya lo da {@code EvidenciaResponse}). Espejo, en forma de filtro, del
     * arco exclusivo de {@code evidence.api.DestinoEvidencia}. */
    enum TipoDestino {
        REGISTRO_HABITO,
        ROCA_DIARIA,
        REGISTRO_ESPIRITU
    }

    record ListarEvidenciaComando(UserId actorId, UserId participanteId, EstadoValidacion estado,
                                   TipoDestino tipoDestino, Instant desde, Instant hasta, Instant cursor) {

        public ListarEvidenciaComando {
            Objects.requireNonNull(actorId, "actorId es obligatorio");
            if (desde != null && hasta != null && desde.isAfter(hasta)) {
                throw new IllegalArgumentException("desde no puede ser posterior a hasta");
            }
        }
    }

    record PaginaEvidencias(List<Evidencia> evidencias, Instant siguienteCursor) {
    }
}
