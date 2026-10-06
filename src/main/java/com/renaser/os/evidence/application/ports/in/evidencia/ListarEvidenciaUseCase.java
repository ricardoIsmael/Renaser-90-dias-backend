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
 *   <li>Sin {@code participanteId}, cualquier rol: SOLO la propia (E-542).</li>
 *   <li>ADMIN/ALCHEMIST: con {@code participanteId}, la de cualquier participante.
 *       <b>Corregido 2026-10-06 (E-542).</b> Decía «cualquier evidencia, {@code participanteId}
 *       opcional»: sin filtro devolvía la de todo el padrón, con foto firmada (D-252), y la app
 *       lo usa sin filtro para «Mis evidencias». El listado de todas es
 *       {@code GET /admin/evidence} ({@link ListarEvidenciaAdminUseCase}, sin foto).</li>
 *   <li>MENTOR: sin {@code participanteId} (o con el suyo), su propia evidencia — un mentor
 *       también puede cursar el programa (D-07). Para la de un aprendiz tiene que nombrarlo
 *       ({@code participanteId}; no hay forma pública de listar "todos mis aprendices" — ver
 *       docs/MODULO_EVIDENCE.md) y ser el mentor asignado a ese aprendiz, según
 *       {@code users.api.ParticipacionProgramaFinder} (mismo puerto y mismo criterio que
 *       {@code support.TicketMentorService.requireMentorAsignado}).
 *       <b>Corregido 2026-10-05 (E-519).</b> Decía que para el MENTOR {@code participanteId} era
 *       OBLIGATORIO; desde el arreglo de la autoconsulta ({@code EvidenciaService.resolverFiltroSegunRol})
 *       no lo es, y este javadoc había quedado atrás.</li>
 *   <li>Cualquier otro rol (TRAINEE, MENTOR_LEAD): solo la propia — {@code participanteId}
 *       nulo o igual a {@code actorId}; cualquier otro valor es 403.</li>
 * </ul>
 *
 * <p><b>La foto de cada evidencia (D-252, 2026-10-05).</b> Cada evidencia con foto viaja con su URL de
 * lectura firmada ({@link EvidenciaListada#fotoUrl()}), para que Yo muestre la foto real y no un
 * ícono de cámara. Se firma DESPUÉS de autorizar el listado (las reglas de arriba), así que la URL
 * la recibe solo quien ya podía abrir esa evidencia por {@code GET /api/v1/evidence/{id}/url}.
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

    /**
     * Una fila del listado.
     *
     * @param fotoUrl URL de lectura firmada de la foto, o {@code null} si la evidencia no tiene una
     *                ({@link Evidencia#tieneFoto()}) o si el listado no firma (el del panel admin)
     */
    record EvidenciaListada(Evidencia evidencia, String fotoUrl) {

        public EvidenciaListada {
            Objects.requireNonNull(evidencia, "evidencia es obligatoria");
        }

        public static EvidenciaListada sinFoto(Evidencia evidencia) {
            return new EvidenciaListada(evidencia, null);
        }
    }

    /**
     * <b>Corregido 2026-10-05 (D-252).</b> Era {@code List<Evidencia>}: el listado no llevaba la foto
     * y la app pintaba un ícono de cámara en cada miniatura.
     */
    record PaginaEvidencias(List<EvidenciaListada> evidencias, Instant siguienteCursor) {
    }
}
