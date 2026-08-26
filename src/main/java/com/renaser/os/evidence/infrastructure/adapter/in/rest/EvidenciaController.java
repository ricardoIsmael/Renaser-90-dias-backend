package com.renaser.os.evidence.infrastructure.adapter.in.rest;

import com.renaser.os.evidence.api.EstadoValidacion;
import com.renaser.os.evidence.application.ports.in.evidencia.ConsultarEvidenciaUseCase;
import com.renaser.os.evidence.application.ports.in.evidencia.ListarEvidenciaUseCase;
import com.renaser.os.evidence.application.ports.in.evidencia.ListarEvidenciaUseCase.ListarEvidenciaComando;
import com.renaser.os.evidence.application.ports.in.evidencia.ListarEvidenciaUseCase.TipoDestino;
import com.renaser.os.evidence.domain.model.evidencia.EvidenciaId;
import com.renaser.os.shared.domain.UserId;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

/**
 * Actor resuelto por header {@code X-Actor-Id} (temporal, D-29 de {@code users}, mismo
 * patrón que {@code points}/{@code phasecontracts}/{@code rocks}/{@code habits}).
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
    public EvidenciaResponse porId(@RequestHeader("X-Actor-Id") String actorId, @PathVariable UUID id) {
        var evidencia = consultarUseCase.porId(UserId.of(actorId), EvidenciaId.of(id));
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
    public EvidenciaPageResponse listar(@RequestHeader("X-Actor-Id") String actorId,
                                         @RequestParam(required = false) UUID participanteId,
                                         @RequestParam(required = false) String estado,
                                         @RequestParam(required = false) String tipoDestino,
                                         @RequestParam(required = false) String desde,
                                         @RequestParam(required = false) String hasta,
                                         @RequestParam(required = false) String cursor) {
        var comando = new ListarEvidenciaComando(UserId.of(actorId),
                participanteId != null ? UserId.of(participanteId) : null,
                estado != null ? EstadoValidacion.valueOf(estado) : null,
                tipoDestino != null ? TipoDestino.valueOf(tipoDestino) : null,
                desde != null ? Instant.parse(desde) : null, hasta != null ? Instant.parse(hasta) : null,
                cursor != null ? Instant.parse(cursor) : null);
        return EvidenciaPageResponse.from(listarUseCase.listar(comando));
    }
}
