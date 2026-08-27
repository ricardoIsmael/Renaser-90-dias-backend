package com.renaser.os.notifications.infrastructure.adapter.in.rest.notificacion;

import com.renaser.os.notifications.application.ports.in.notificacion.ListarNotificacionesUseCase;
import com.renaser.os.notifications.application.ports.in.notificacion.MarcarLeidaUseCase;
import com.renaser.os.notifications.application.ports.in.notificacion.MarcarTodasLeidasUseCase;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Rutas fieles al contrato viejo: {@code GET /notifications}, {@code PUT /notifications/:id/read},
 * {@code PUT /notifications/read-all} (`notifications/route.ts`, `[id]/read/route.ts`,
 * `read-all/route.ts`). El actor se resuelve con {@code @ActorAutenticado} desde la sesion, con
 * respaldo por el header X-Actor-Id mientras siga vivo (era el mecanismo temporal original, D-29
 * de `users`, mismo patron que `points`/`phasecontracts`/`support`). Autoservicio estricto: nunca
 * la bandeja de otro. */
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificacionController {

    private final ListarNotificacionesUseCase listarNotificacionesUseCase;
    private final MarcarLeidaUseCase marcarLeidaUseCase;
    private final MarcarTodasLeidasUseCase marcarTodasLeidasUseCase;

    public NotificacionController(ListarNotificacionesUseCase listarNotificacionesUseCase,
                                   MarcarLeidaUseCase marcarLeidaUseCase,
                                   MarcarTodasLeidasUseCase marcarTodasLeidasUseCase) {
        this.listarNotificacionesUseCase = listarNotificacionesUseCase;
        this.marcarLeidaUseCase = marcarLeidaUseCase;
        this.marcarTodasLeidasUseCase = marcarTodasLeidasUseCase;
    }

    @GetMapping
    public NotificacionesBandejaResponse listar(@ActorAutenticado UserId actor) {
        return NotificacionesBandejaResponse.from(listarNotificacionesUseCase.listar(actor));
    }

    @PutMapping("/{id}/read")
    public MarcarLeidaResponse marcarLeida(@ActorAutenticado UserId actor, @PathVariable Long id) {
        return MarcarLeidaResponse.from(marcarLeidaUseCase.marcarLeida(actor, id));
    }

    @PutMapping("/read-all")
    public MarcarTodasLeidasResponse marcarTodasLeidas(@ActorAutenticado UserId actor) {
        return new MarcarTodasLeidasResponse(marcarTodasLeidasUseCase.marcarTodas(actor));
    }
}
