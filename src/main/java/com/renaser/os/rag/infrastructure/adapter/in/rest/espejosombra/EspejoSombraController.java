package com.renaser.os.rag.infrastructure.adapter.in.rest.espejosombra;

import com.renaser.os.rag.application.ports.in.espejosombra.ListarInformesEspejoSombraUseCase;
import com.renaser.os.rag.application.ports.in.espejosombra.ObtenerInformeEspejoSombraUseCase;
import com.renaser.os.rag.domain.model.espejosombra.InformeEspejoSombraId;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Actor resuelto por {@code @ActorAutenticado}: primero desde la sesion y, si no hay,
 * desde el header {@code X-Actor-Id} (respaldo temporal, D-29 de {@code users}, mismo
 * patrón que {@code evidence}/{@code rocks}/{@code support}). Sin
 * {@code participanteId} en la query, lista los informes del propio actor; con
 * {@code participanteId}, los de ESE participante — visible solo si el actor es el
 * propio participante, su mentor asignado, o ADMIN/ALCHEMIST (D-47, verificado
 * dentro de {@code EspejoSombraService}, nunca en el controller).
 *
 * <p>No existe un endpoint para generar un informe a demanda: nace solo del
 * scheduler semanal (docs/MODULO_RAG.md §4).
 */
@RestController
@RequestMapping("/api/v1/espejo-sombra")
public class EspejoSombraController {

    private final ListarInformesEspejoSombraUseCase listarUseCase;
    private final ObtenerInformeEspejoSombraUseCase obtenerUseCase;

    public EspejoSombraController(ListarInformesEspejoSombraUseCase listarUseCase,
                                   ObtenerInformeEspejoSombraUseCase obtenerUseCase) {
        this.listarUseCase = listarUseCase;
        this.obtenerUseCase = obtenerUseCase;
    }

    @GetMapping
    public List<InformeEspejoSombraResponse> listar(@ActorAutenticado UserId actorId,
                                                      @RequestParam(name = "participanteId", required = false)
                                                      UUID participanteIdParam) {
        UserId participanteId = participanteIdParam != null ? UserId.of(participanteIdParam) : actorId;
        return listarUseCase.deParticipante(actorId, participanteId).stream()
                .map(InformeEspejoSombraResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    public InformeEspejoSombraResponse porId(@ActorAutenticado UserId actorId,
                                              @PathVariable UUID id) {
        var informe = obtenerUseCase.porId(actorId, InformeEspejoSombraId.of(id));
        return InformeEspejoSombraResponse.from(informe);
    }
}
