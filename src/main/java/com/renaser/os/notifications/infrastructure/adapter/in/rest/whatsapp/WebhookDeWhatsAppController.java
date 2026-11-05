package com.renaser.os.notifications.infrastructure.adapter.in.rest.whatsapp;

import com.renaser.os.notifications.application.ports.in.whatsapp.AtenderWebhookDeWhatsAppUseCase;
import com.renaser.os.shared.web.security.PublicEndpoint;
import com.renaser.os.shared.web.security.SinInterruptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * El webhook de YCloud (spec 041, FR-026): {@code POST /api/v1/webhooks/whatsapp}, <b>sin sesion</b> porque lo abre el proveedor. Su unica puerta es la firma
 * ({@link FirmaDelWebhookDeWhatsApp}); sin ella no entra nada. Atiende el estado de un mensaje nuestro y la baja; <b>nada de lo que la gente escribe se guarda</b>.
 *
 * <p>Si algo falla al atenderlo responde 500 y YCloud reintenta: las dos operaciones se pueden repetir sin dano (el estado no retrocede y una baja dos veces es una baja).
 * Una baja se respeta SIEMPRE, tambien con {@code eventos.whatsapp} apagado: por eso no tiene interruptor.
 */
@RestController
@RequestMapping("/api/v1/webhooks/whatsapp")
@SinInterruptor(motivo = "Una baja se respeta siempre y el proveedor reintenta lo que no se atiende; apagar el envio no puede dejar de atender las bajas.")
class WebhookDeWhatsAppController {

    private final FirmaDelWebhookDeWhatsApp firma;
    private final LectorDelWebhookDeYCloud lector;
    private final AtenderWebhookDeWhatsAppUseCase atender;

    WebhookDeWhatsAppController(FirmaDelWebhookDeWhatsApp firma, LectorDelWebhookDeYCloud lector, AtenderWebhookDeWhatsAppUseCase atender) {
        this.firma = firma;
        this.lector = lector;
        this.atender = atender;
    }

    @PublicEndpoint("Lo abre YCloud, que no tiene sesion: la credencial es la firma YCloud-Signature (HMAC con el secreto del endpoint). Sin firma valida no se procesa nada.")
    @PostMapping
    ResponseEntity<Map<String, String>> recibir(@RequestBody byte[] cuerpo,
                                                @RequestHeader(name = "YCloud-Signature", required = false) String cabecera) {
        return switch (firma.revisar(cuerpo, cabecera)) {
            case SIN_SECRETO -> respuesta(HttpStatus.INTERNAL_SERVER_ERROR, "Falta el secreto del webhook: no se procesa nada.");
            case FIRMA_INVALIDA -> respuesta(HttpStatus.UNAUTHORIZED, "Firma no válida.");
            case VALIDA -> atenderElAviso(cuerpo);
        };
    }

    private ResponseEntity<Map<String, String>> atenderElAviso(byte[] cuerpo) {
        var aviso = lector.leer(cuerpo);
        if (aviso.isEmpty()) {
            return respuesta(HttpStatus.BAD_REQUEST, "El cuerpo no es JSON.");
        }
        switch (aviso.get()) {
            case LectorDelWebhookDeYCloud.Estado estado -> atender.estadoDeUnMensaje(estado.datos());
            case LectorDelWebhookDeYCloud.Respuesta respuesta -> atender.respuestaRecibida(respuesta.datos());
            case LectorDelWebhookDeYCloud.Otro otro -> { }
        }
        return respuesta(HttpStatus.OK, "ok");
    }

    private static ResponseEntity<Map<String, String>> respuesta(HttpStatus estado, String texto) {
        return ResponseEntity.status(estado).body(Map.of(estado.is2xxSuccessful() ? "resultado" : "error", texto));
    }
}
