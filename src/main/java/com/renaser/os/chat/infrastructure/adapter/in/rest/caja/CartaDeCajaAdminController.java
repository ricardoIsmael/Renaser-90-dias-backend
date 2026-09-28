package com.renaser.os.chat.infrastructure.adapter.in.rest.caja;

import com.renaser.os.chat.application.ports.in.caja.CartaDeCajaUseCase;
import com.renaser.os.chat.application.ports.in.caja.CartaDeCajaUseCase.FondoDeLaCarta;
import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import com.renaser.os.shared.web.security.RequiresPermission;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

/**
 * La carta con el nombre de la Caja Renaser (D-219): descargarla lista para imprimir y cambiarle el fondo.
 * Cuelga de {@code /admin/caja} como el resto de la caja, aunque la dibuje {@code chat}.
 */
@RestController
@RequestMapping("/api/v1/admin/caja")
public class CartaDeCajaAdminController {

    private static final String SOLO_ADMIN = "solo ADMIN activo (D-219): el servicio rechaza a los demás roles, "
            + "ALCHEMIST incluido, y a las cuentas suspendidas";

    private final CartaDeCajaUseCase carta;

    public CartaDeCajaAdminController(CartaDeCajaUseCase carta) {
        this.carta = carta;
    }

    @RequiresPermission(value = Permission.MANAGE_RENASER_BOX, scope = SOLO_ADMIN)
    @GetMapping("/{aprendizId}/carta")
    public ResponseEntity<byte[]> carta(@ActorAutenticado UserId actorId, @PathVariable UUID aprendizId) {
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename("carta-" + aprendizId + ".png").build().toString())
                .body(carta.carta(actorId, UserId.of(aprendizId)));
    }

    @RequiresPermission(value = Permission.MANAGE_RENASER_BOX, scope = SOLO_ADMIN)
    @GetMapping("/carta/fondo")
    public FondoResponse fondo(@ActorAutenticado UserId actorId) {
        return FondoResponse.from(carta.fondo(actorId));
    }

    @RequiresPermission(value = Permission.MANAGE_RENASER_BOX, scope = SOLO_ADMIN)
    @PostMapping("/carta/fondo/upload-url")
    public UrlResponse subirFondo(@ActorAutenticado UserId actorId, @RequestBody(required = false) SubidaRequest request) {
        var subida = carta.solicitarSubidaDeFondo(actorId, request == null ? null : request.contentType());
        return new UrlResponse(subida.url().toString(), subida.ruta());
    }

    @RequiresPermission(value = Permission.MANAGE_RENASER_BOX, scope = SOLO_ADMIN)
    @PostMapping("/carta/fondo/confirm")
    public FondoResponse confirmarFondo(@ActorAutenticado UserId actorId, @RequestBody ConfirmarRequest request) {
        return FondoResponse.from(carta.confirmarFondo(actorId, request.ruta()));
    }

    @RequiresPermission(value = Permission.MANAGE_RENASER_BOX, scope = SOLO_ADMIN)
    @DeleteMapping("/carta/fondo")
    public FondoResponse volverAlOriginal(@ActorAutenticado UserId actorId) {
        return FondoResponse.from(carta.volverAlFondoOriginal(actorId));
    }

    /** {@code image/jpeg} o {@code image/png}. */
    record SubidaRequest(String contentType) {
    }

    record ConfirmarRequest(String ruta) {
    }

    record UrlResponse(String url, String ruta) {
    }

    record FondoResponse(boolean cambiado, boolean sePuedeCambiar, String cambiadoPor, Instant cambiadoEn) {

        static FondoResponse from(FondoDeLaCarta fondo) {
            return new FondoResponse(fondo.cambiado(), fondo.sePuedeCambiar(), fondo.cambiadoPor(), fondo.cambiadoEn());
        }
    }
}
