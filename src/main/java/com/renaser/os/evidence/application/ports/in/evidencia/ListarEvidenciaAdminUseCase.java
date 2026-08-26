package com.renaser.os.evidence.application.ports.in.evidencia;

import com.renaser.os.evidence.api.EstadoValidacion;
import com.renaser.os.evidence.application.ports.in.evidencia.ListarEvidenciaUseCase.PaginaEvidencias;
import com.renaser.os.evidence.application.ports.in.evidencia.ListarEvidenciaUseCase.TipoDestino;
import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.util.Objects;

/**
 * Listado de evidencia para el panel admin (hueco #20) — típicamente
 * {@code estado=REVISION_MANUAL}, la cola de revisión humana, pero sin fijar ese filtro
 * a mano acá: el cliente (panel admin) lo pide explícito por query param, este caso de
 * uso solo garantiza el gate de rol. Deliberadamente separado de
 * {@link ListarEvidenciaUseCase}: acá NO hay ninguna lógica de "dueño" ni de mentor
 * asignado, es una vista de plataforma sin scoping — mismo criterio de
 * {@code support.TicketMentorService.todos} (MENTOR_LEAD/ADMIN/ALCHEMIST, vista sin
 * scoping) salvo que acá, igual que {@code revisar}/{@code anular} de este mismo módulo,
 * se restringe a ADMIN/ALCHEMIST (ver pregunta abierta #1 de docs/MODULO_EVIDENCE.md:
 * MENTOR_LEAD no confirmado).
 */
public interface ListarEvidenciaAdminUseCase {

    PaginaEvidencias listar(ListarEvidenciaAdminComando comando);

    record ListarEvidenciaAdminComando(UserId actorId, UserId participanteId, EstadoValidacion estado,
                                        TipoDestino tipoDestino, Instant desde, Instant hasta, Instant cursor) {

        public ListarEvidenciaAdminComando {
            Objects.requireNonNull(actorId, "actorId es obligatorio");
            if (desde != null && hasta != null && desde.isAfter(hasta)) {
                throw new IllegalArgumentException("desde no puede ser posterior a hasta");
            }
        }
    }
}
