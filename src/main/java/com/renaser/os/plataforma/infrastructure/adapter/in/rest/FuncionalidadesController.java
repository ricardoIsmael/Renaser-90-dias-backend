package com.renaser.os.plataforma.infrastructure.adapter.in.rest;

import com.renaser.os.plataforma.application.ports.in.ConsultarMisFuncionalidadesUseCase;
import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import com.renaser.os.shared.web.security.RequiresPermission;
import com.renaser.os.shared.web.security.SinInterruptor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /api/v1/funcionalidades/mias} (contrato §6): lo apagado para esta sesión. La app lo lee al entrar, al
 * volver al frente y cada 60 s con {@code If-None-Match}; si nada cambió, 304 sin cuerpo.
 */
@RestController
@RequestMapping("/api/v1/funcionalidades")
@SinInterruptor(motivo = "La app lee acá qué está apagado; si se pudiera apagar, la app no se enteraría de nada "
        + "(anti-bloqueo, FR-004)")
class FuncionalidadesController {

    private final ConsultarMisFuncionalidadesUseCase misFuncionalidades;

    FuncionalidadesController(ConsultarMisFuncionalidadesUseCase misFuncionalidades) {
        this.misFuncionalidades = misFuncionalidades;
    }

    @RequiresPermission(Permission.USE_APP)
    @GetMapping("/mias")
    public ResponseEntity<MisFuncionalidadesResponse> mias(@ActorAutenticado UserId actor,
                                                           @RequestHeader(value = HttpHeaders.IF_NONE_MATCH,
                                                                   required = false) String siNoCoincide) {
        MisFuncionalidadesResponse respuesta = MisFuncionalidadesResponse.from(misFuncionalidades.mias(actor));
        String etag = respuesta.etag();
        if (siNoCoincide != null && siNoCoincide.contains(etag)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(etag).build();
        }
        return ResponseEntity.ok().eTag(etag).cacheControl(CacheControl.noCache().cachePrivate()).body(respuesta);
    }
}
