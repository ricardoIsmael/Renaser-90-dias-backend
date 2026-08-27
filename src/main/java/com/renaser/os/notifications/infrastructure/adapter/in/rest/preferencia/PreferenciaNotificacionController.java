package com.renaser.os.notifications.infrastructure.adapter.in.rest.preferencia;

import com.renaser.os.notifications.application.ports.in.preferencia.GestionarPreferenciasUseCase;
import com.renaser.os.notifications.application.ports.in.preferencia.GestionarPreferenciasUseCase.ActualizarPreferenciasCommand;
import com.renaser.os.notifications.application.ports.in.preferencia.GestionarPreferenciasUseCase.ItemPreferencia;
import com.renaser.os.notifications.domain.model.notificacion.TipoNotificacion;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Rutas fieles al contrato viejo: {@code GET/PATCH /notification-preferences}
 * (`notification-preferences/route.ts`, P-02/P-03). Autoservicio: siempre las preferencias
 * del actor resuelto, nunca las de otro usuario. */
@RestController
@RequestMapping("/api/v1/notification-preferences")
public class PreferenciaNotificacionController {

    private final GestionarPreferenciasUseCase gestionarPreferenciasUseCase;

    public PreferenciaNotificacionController(GestionarPreferenciasUseCase gestionarPreferenciasUseCase) {
        this.gestionarPreferenciasUseCase = gestionarPreferenciasUseCase;
    }

    @GetMapping
    public PreferenciasResponse consultar(@ActorAutenticado UserId actor) {
        return PreferenciasResponse.from(gestionarPreferenciasUseCase.consultar(actor));
    }

    @PatchMapping
    public PreferenciasResponse actualizar(@ActorAutenticado UserId actor,
                                            @RequestBody @Valid ActualizarPreferenciasRequest request) {
        var items = request.preferences().stream()
                .map(item -> new ItemPreferencia(TipoNotificacion.valueOf(item.type()), item.enabled()))
                .toList();
        var actualizadas = gestionarPreferenciasUseCase.actualizar(
                new ActualizarPreferenciasCommand(actor, items));
        return PreferenciasResponse.from(actualizadas);
    }
}
