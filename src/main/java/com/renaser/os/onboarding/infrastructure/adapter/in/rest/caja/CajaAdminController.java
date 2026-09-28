package com.renaser.os.onboarding.infrastructure.adapter.in.rest.caja;

import com.renaser.os.onboarding.application.ports.in.caja.ConsultarCajasUseCase;
import com.renaser.os.onboarding.application.ports.in.caja.ConsultarCajasUseCase.FiltroDeCajas;
import com.renaser.os.onboarding.application.ports.in.caja.ContenidoDeCajaUseCase;
import com.renaser.os.onboarding.application.ports.in.caja.ContenidoDeCajaUseCase.PedidoDeElemento;
import com.renaser.os.onboarding.application.ports.in.caja.VerCajaUseCase;
import com.renaser.os.onboarding.domain.model.caja.EstadoCaja;
import com.renaser.os.onboarding.infrastructure.adapter.in.rest.caja.CajaRequests.ContenidoRequest;
import com.renaser.os.onboarding.infrastructure.adapter.in.rest.caja.CajaResponses.ContenidoResponse;
import com.renaser.os.onboarding.infrastructure.adapter.in.rest.caja.CajaResponses.DetalleResponse;
import com.renaser.os.onboarding.infrastructure.adapter.in.rest.caja.CajaResponses.PaginaResponse;
import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import com.renaser.os.shared.web.security.RequiresPermission;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * Administración → «Caja Renaser»: la lista con sus pestañas, la descarga, el detalle y la lista de lo que
 * lleva la caja (D-219). Lo que se hace con cada caja está en {@link OperacionesDeCajaController}.
 */
@RestController
@RequestMapping("/api/v1/admin/caja")
public class CajaAdminController {

    static final String SOLO_ADMIN = "solo ADMIN activo (D-219): el servicio rechaza a los demás roles, ALCHEMIST "
            + "incluido, y a las cuentas suspendidas";
    private static final MediaType CSV = new MediaType("text", "csv", StandardCharsets.UTF_8);

    private final ConsultarCajasUseCase consultar;
    private final VerCajaUseCase ver;
    private final ContenidoDeCajaUseCase contenido;

    public CajaAdminController(ConsultarCajasUseCase consultar, VerCajaUseCase ver, ContenidoDeCajaUseCase contenido) {
        this.consultar = consultar;
        this.ver = ver;
        this.contenido = contenido;
    }

    @RequiresPermission(value = Permission.MANAGE_RENASER_BOX, scope = SOLO_ADMIN)
    @GetMapping
    public PaginaResponse listar(@ActorAutenticado UserId actorId,
                                 @RequestParam(required = false) EstadoCaja estado,
                                 @RequestParam(required = false) String q,
                                 @RequestParam(defaultValue = "0") int page,
                                 @RequestParam(defaultValue = "50") int size) {
        return PaginaResponse.from(consultar.listar(actorId, new FiltroDeCajas(estado, q, page, size)));
    }

    @RequiresPermission(value = Permission.MANAGE_RENASER_BOX, scope = SOLO_ADMIN)
    @GetMapping("/export.csv")
    public ResponseEntity<byte[]> exportar(@ActorAutenticado UserId actorId) {
        return ResponseEntity.ok()
                .contentType(CSV)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("caja-renaser.csv").build().toString())
                .body(CsvDeCajas.escribir(consultar.exportar(actorId)));
    }

    @RequiresPermission(value = Permission.MANAGE_RENASER_BOX, scope = SOLO_ADMIN)
    @GetMapping("/contenido")
    public ContenidoResponse contenido(@ActorAutenticado UserId actorId) {
        return ContenidoResponse.from(contenido.ver(actorId));
    }

    @RequiresPermission(value = Permission.MANAGE_RENASER_BOX, scope = SOLO_ADMIN)
    @PutMapping("/contenido")
    public ContenidoResponse cambiarContenido(@ActorAutenticado UserId actorId, @RequestBody ContenidoRequest request) {
        List<PedidoDeElemento> elementos = request.elementos() == null ? null : request.elementos().stream()
                .map(e -> e == null ? null : new PedidoDeElemento(e.valor(), e.etiqueta())).toList();
        return ContenidoResponse.from(contenido.reemplazar(actorId, elementos));
    }

    @RequiresPermission(value = Permission.MANAGE_RENASER_BOX, scope = SOLO_ADMIN)
    @GetMapping("/{aprendizId}")
    public DetalleResponse detalle(@ActorAutenticado UserId actorId, @PathVariable UUID aprendizId) {
        return DetalleResponse.from(ver.ver(actorId, UserId.of(aprendizId)));
    }
}
