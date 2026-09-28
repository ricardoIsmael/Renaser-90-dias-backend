package com.renaser.os.onboarding.infrastructure.adapter.in.rest.caja;

import com.renaser.os.onboarding.application.ports.in.caja.FotosDeCajaUseCase;
import com.renaser.os.onboarding.application.ports.in.caja.FotosDeCajaUseCase.UrlDeSubida;
import com.renaser.os.onboarding.application.ports.in.caja.OperarCajaUseCase;
import com.renaser.os.onboarding.application.ports.in.caja.OperarCajaUseCase.PedidoDeEnvio;
import com.renaser.os.onboarding.domain.model.caja.CajaIncompletaException;
import com.renaser.os.onboarding.domain.model.caja.FotoDeCaja;
import com.renaser.os.onboarding.infrastructure.adapter.in.rest.caja.CajaRequests.ConfirmarFotoRequest;
import com.renaser.os.onboarding.infrastructure.adapter.in.rest.caja.CajaRequests.EntregadaRequest;
import com.renaser.os.onboarding.infrastructure.adapter.in.rest.caja.CajaRequests.EnviarRequest;
import com.renaser.os.onboarding.infrastructure.adapter.in.rest.caja.CajaRequests.MarcadosRequest;
import com.renaser.os.onboarding.infrastructure.adapter.in.rest.caja.CajaRequests.ProblemaRequest;
import com.renaser.os.onboarding.infrastructure.adapter.in.rest.caja.CajaRequests.SubidaRequest;
import com.renaser.os.onboarding.infrastructure.adapter.in.rest.caja.CajaResponses.CajaIncompletaResponse;
import com.renaser.os.onboarding.infrastructure.adapter.in.rest.caja.CajaResponses.DetalleResponse;
import com.renaser.os.onboarding.infrastructure.adapter.in.rest.caja.CajaResponses.UrlDeSubidaResponse;
import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import com.renaser.os.shared.web.security.RequiresPermission;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

/**
 * Lo que el Admin hace con UNA caja (D-219, spec §9): cada botón es una ruta y todas devuelven el detalle
 * actualizado. Si el estado no corresponde, 409 con el motivo; si al enviar falta algo, 409 con
 * {@code faltan}.
 */
@RestController
@RequestMapping("/api/v1/admin/caja/{aprendizId}")
public class OperacionesDeCajaController {

    private static final String SOLO_ADMIN = CajaAdminController.SOLO_ADMIN;

    private final OperarCajaUseCase operar;
    private final FotosDeCajaUseCase fotos;

    public OperacionesDeCajaController(OperarCajaUseCase operar, FotosDeCajaUseCase fotos) {
        this.operar = operar;
        this.fotos = fotos;
    }

    @RequiresPermission(value = Permission.MANAGE_RENASER_BOX, scope = SOLO_ADMIN)
    @PostMapping("/aprobar")
    public DetalleResponse aprobar(@ActorAutenticado UserId actorId, @PathVariable UUID aprendizId) {
        return DetalleResponse.from(operar.aprobar(actorId, UserId.of(aprendizId)));
    }

    @RequiresPermission(value = Permission.MANAGE_RENASER_BOX, scope = SOLO_ADMIN)
    @PostMapping("/armar")
    public DetalleResponse armar(@ActorAutenticado UserId actorId, @PathVariable UUID aprendizId) {
        return DetalleResponse.from(operar.armar(actorId, UserId.of(aprendizId)));
    }

    @RequiresPermission(value = Permission.MANAGE_RENASER_BOX, scope = SOLO_ADMIN)
    @PutMapping("/contenido")
    public DetalleResponse marcarContenido(@ActorAutenticado UserId actorId, @PathVariable UUID aprendizId,
                                           @RequestBody MarcadosRequest request) {
        return DetalleResponse.from(operar.marcarContenido(actorId, UserId.of(aprendizId), request.marcados()));
    }

    @RequiresPermission(value = Permission.MANAGE_RENASER_BOX, scope = SOLO_ADMIN)
    @PostMapping("/enviar")
    public DetalleResponse enviar(@ActorAutenticado UserId actorId, @PathVariable UUID aprendizId,
                                  @RequestBody EnviarRequest request) {
        return DetalleResponse.from(operar.enviar(actorId, UserId.of(aprendizId),
                new PedidoDeEnvio(request.medio(), request.courier(), request.codigo(), request.costo())));
    }

    @RequiresPermission(value = Permission.MANAGE_RENASER_BOX, scope = SOLO_ADMIN)
    @PostMapping("/entregada")
    public DetalleResponse entregada(@ActorAutenticado UserId actorId, @PathVariable UUID aprendizId,
                                     @RequestBody(required = false) EntregadaRequest request) {
        boolean previa = request != null && Boolean.TRUE.equals(request.previa());
        return DetalleResponse.from(operar.marcarEntregada(actorId, UserId.of(aprendizId), previa));
    }

    @RequiresPermission(value = Permission.MANAGE_RENASER_BOX, scope = SOLO_ADMIN)
    @PostMapping("/problema")
    public DetalleResponse problema(@ActorAutenticado UserId actorId, @PathVariable UUID aprendizId,
                                    @RequestBody ProblemaRequest request) {
        return DetalleResponse.from(operar.reportarProblema(actorId, UserId.of(aprendizId), request.motivo(),
                request.nota()));
    }

    @RequiresPermission(value = Permission.MANAGE_RENASER_BOX, scope = SOLO_ADMIN)
    @PostMapping("/reenviar")
    public DetalleResponse reenviar(@ActorAutenticado UserId actorId, @PathVariable UUID aprendizId) {
        return DetalleResponse.from(operar.reenviar(actorId, UserId.of(aprendizId)));
    }

    @RequiresPermission(value = Permission.MANAGE_RENASER_BOX, scope = SOLO_ADMIN)
    @PostMapping("/foto/upload-url")
    public UrlDeSubidaResponse subirFoto(@ActorAutenticado UserId actorId, @PathVariable UUID aprendizId,
                                         @RequestBody(required = false) SubidaRequest request) {
        return url(fotos.solicitarSubida(actorId, UserId.of(aprendizId), FotoDeCaja.ARMADA, tipo(request)));
    }

    @RequiresPermission(value = Permission.MANAGE_RENASER_BOX, scope = SOLO_ADMIN)
    @PostMapping("/foto/confirm")
    public DetalleResponse confirmarFoto(@ActorAutenticado UserId actorId, @PathVariable UUID aprendizId,
                                         @RequestBody ConfirmarFotoRequest request) {
        return DetalleResponse.from(fotos.confirmar(actorId, UserId.of(aprendizId), FotoDeCaja.ARMADA, request.ruta()));
    }

    @RequiresPermission(value = Permission.MANAGE_RENASER_BOX, scope = SOLO_ADMIN)
    @PostMapping("/comprobante/upload-url")
    public UrlDeSubidaResponse subirComprobante(@ActorAutenticado UserId actorId, @PathVariable UUID aprendizId,
                                                @RequestBody(required = false) SubidaRequest request) {
        return url(fotos.solicitarSubida(actorId, UserId.of(aprendizId), FotoDeCaja.COMPROBANTE, tipo(request)));
    }

    @RequiresPermission(value = Permission.MANAGE_RENASER_BOX, scope = SOLO_ADMIN)
    @PostMapping("/comprobante/confirm")
    public DetalleResponse confirmarComprobante(@ActorAutenticado UserId actorId, @PathVariable UUID aprendizId,
                                                @RequestBody ConfirmarFotoRequest request) {
        return DetalleResponse.from(fotos.confirmar(actorId, UserId.of(aprendizId), FotoDeCaja.COMPROBANTE,
                request.ruta()));
    }

    /** El 409 de «enviar» con lo que falta, para que la app lo marque sin adivinar. */
    @ExceptionHandler(CajaIncompletaException.class)
    public ResponseEntity<CajaIncompletaResponse> incompleta(CajaIncompletaException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).contentType(MediaType.APPLICATION_JSON)
                .body(new CajaIncompletaResponse(e.getMessage(), e.faltan(), Instant.now()));
    }

    private static String tipo(SubidaRequest request) {
        return request == null ? null : request.contentType();
    }

    private static UrlDeSubidaResponse url(UrlDeSubida subida) {
        return new UrlDeSubidaResponse(subida.url().toString(), subida.ruta());
    }
}
