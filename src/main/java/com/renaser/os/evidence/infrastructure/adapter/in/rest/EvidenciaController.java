package com.renaser.os.evidence.infrastructure.adapter.in.rest;

import com.renaser.os.evidence.api.EstadoValidacion;
import com.renaser.os.evidence.application.ports.in.evidencia.ConsultarEvidenciaUseCase;
import com.renaser.os.evidence.application.ports.in.evidencia.ListarEvidenciaUseCase;
import com.renaser.os.evidence.application.ports.in.evidencia.ListarEvidenciaUseCase.ListarEvidenciaComando;
import com.renaser.os.evidence.application.ports.in.evidencia.ListarEvidenciaUseCase.TipoDestino;
import com.renaser.os.evidence.domain.model.evidencia.EvidenciaId;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

/**
 * Actor resuelto con {@code @ActorAutenticado}: primero desde la sesion y, si no hay,
 * desde el header {@code X-Actor-Id} como respaldo (el mecanismo temporal original,
 * D-29 de {@code users}) — ver {@code ActorAutenticadoArgumentResolver}.
 * Autoservicio con excepción admin: el dueño ve su propia evidencia; cualquier otro
 * actor necesita ser ADMIN/ALCHEMIST (aplicado dentro de {@code EvidenciaService}). El
 * listado ({@code GET} sin id) suma un tercer caso, MENTOR con su aprendiz asignado —
 * ver javadoc de {@link ListarEvidenciaUseCase}.
 */
@RestController
@RequestMapping("/api/v1/evidence")
public class EvidenciaController {

    private final ConsultarEvidenciaUseCase consultarUseCase;
    private final ListarEvidenciaUseCase listarUseCase;

    public EvidenciaController(ConsultarEvidenciaUseCase consultarUseCase, ListarEvidenciaUseCase listarUseCase) {
        this.consultarUseCase = consultarUseCase;
        this.listarUseCase = listarUseCase;
    }

    @GetMapping("/{id}")
    public EvidenciaResponse porId(@ActorAutenticado UserId actor, @PathVariable UUID id) {
        var evidencia = consultarUseCase.porId(actor, EvidenciaId.of(id));
        return EvidenciaResponse.from(evidencia);
    }

    /**
     * Filtros opcionales (CLAUDE.MD, hueco #19): {@code participanteId}, {@code estado},
     * {@code tipoDestino} (REGISTRO_HABITO/ROCA_DIARIA/REGISTRO_ESPIRITU), rango
     * {@code desde}/{@code hasta} sobre {@code creadoEn}. Paginado por keyset con
     * {@code cursor} — mismo contrato que {@code GET /api/v1/wall}. La autorización
     * (dueño / mentor asignado / admin) vive en {@code EvidenciaService}, no acá.
     */
    @GetMapping
    public EvidenciaPageResponse listar(@ActorAutenticado UserId actor,
                                         @RequestParam(required = false) UUID participanteId,
                                         @RequestParam(required = false) String estado,
                                         @RequestParam(required = false) String tipoDestino,
                                         @RequestParam(required = false) String desde,
                                         @RequestParam(required = false) String hasta,
                                         @RequestParam(required = false) String cursor) {
        var comando = new ListarEvidenciaComando(actor,
                participanteId != null ? UserId.of(participanteId) : null,
                estado != null ? EstadoValidacion.valueOf(estado) : null,
                tipoDestino != null ? TipoDestino.valueOf(tipoDestino) : null,
                desde != null ? Instant.parse(desde) : null, hasta != null ? Instant.parse(hasta) : null,
                cursor != null ? Instant.parse(cursor) : null);
        return EvidenciaPageResponse.from(listarUseCase.listar(comando));
    }
}
