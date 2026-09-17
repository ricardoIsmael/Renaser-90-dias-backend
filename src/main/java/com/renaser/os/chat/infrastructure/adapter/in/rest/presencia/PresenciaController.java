package com.renaser.os.chat.infrastructure.adapter.in.rest.presencia;

import com.renaser.os.chat.application.ports.in.presencia.ConsultarPresenciaUseCase;
import com.renaser.os.chat.domain.model.conversacion.ConversacionId;
import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import com.renaser.os.shared.web.security.RequiresPermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Quien esta en linea en una conversacion, para pintar el indicador al ABRIRLA.
 *
 * <p>A partir de ahi el estado llega solo por el socket, junto con los mensajes
 * ({@code /topic/conversaciones/{id}}, evento {@code PRESENCE}). Este endpoint existe porque
 * una suscripcion entrega cambios y no el estado actual: sin el, quien abre el chat con la
 * otra persona ya conectada no recibiria nada y la veria apagada hasta que se fuera.
 */
@RestController
@RequestMapping("/api/v1/chat/conversations")
public class PresenciaController {

    private final ConsultarPresenciaUseCase consultarPresenciaUseCase;

    public PresenciaController(ConsultarPresenciaUseCase consultarPresenciaUseCase) {
        this.consultarPresenciaUseCase = consultarPresenciaUseCase;
    }

    @RequiresPermission(value = Permission.USE_APP, scope = "participante de la conversacion")
    @GetMapping("/{id}/presence")
    public PresenciaResponse presencia(@ActorAutenticado UserId actorId, @PathVariable UUID id) {
        List<String> enLinea = consultarPresenciaUseCase.enLineaEn(ConversacionId.of(id), actorId).stream()
                .map(usuario -> usuario.value().toString())
                .toList();
        return new PresenciaResponse(enLinea);
    }

    /**
     * Solo los ids de quienes estan conectados; nunca los ausentes ni un "ultima vez".
     *
     * <p>Se devuelve la lista y no un booleano porque la misma respuesta sirve para un 1 a 1 y
     * para un grupo. Y no se manda "ultima vez" porque el sistema no lo sabe: la columna
     * {@code usuarios.ultima_actividad_en} existe desde la migracion V1 y <b>no la escribe
     * nadie</b>. Inventarla aca seria repetir el error que este endpoint vino a corregir.
     */
    public record PresenciaResponse(List<String> online) {
    }
}
