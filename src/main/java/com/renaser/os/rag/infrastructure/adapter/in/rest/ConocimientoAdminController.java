package com.renaser.os.rag.infrastructure.adapter.in.rest;

import com.renaser.os.rag.application.ports.in.conocimiento.IndexarConocimientoUseCase;
import com.renaser.os.rag.application.ports.in.conocimiento.IndexarConocimientoUseCase.IndexarConocimientoCommand;
import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import com.renaser.os.shared.web.security.RequiresPermission;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Solo ADMIN/ALCHEMIST (D-46) — gateado dentro de {@code ConocimientoService}, no acá (CLAUDE.MD sec. 5.4.6). */
@RestController
@RequestMapping("/api/v1/admin/conocimiento")
public class ConocimientoAdminController {

    private final IndexarConocimientoUseCase indexarConocimientoUseCase;

    public ConocimientoAdminController(IndexarConocimientoUseCase indexarConocimientoUseCase) {
        this.indexarConocimientoUseCase = indexarConocimientoUseCase;
    }

    @RequiresPermission(Permission.MANAGE_KNOWLEDGE_BASE)
    @PostMapping
    public ChunkIndexadoResponse indexar(@ActorAutenticado UserId actorId,
                                          @Valid @RequestBody IndexarConocimientoRequest request) {
        var resultado = indexarConocimientoUseCase.indexar(new IndexarConocimientoCommand(actorId,
                request.tipoFuente(), request.clase(), request.documentoId(), request.leccionId(),
                request.contenido(), request.metadatos()));
        return new ChunkIndexadoResponse(resultado.id());
    }
}
