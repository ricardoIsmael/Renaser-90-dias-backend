package com.renaser.os.users.infrastructure.adapter.in.rest.user;

import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import com.renaser.os.shared.web.security.RequiresPermission;
import com.renaser.os.users.application.ports.in.user.GetLogrosUseCase;
import com.renaser.os.users.application.ports.in.user.GetLogrosUseCase.GetLogrosQuery;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Gap #22 (docs/PLAN_INTEGRACION_FRONTEND.md §5) — {@code /api/v1/profile} es un prefijo
 * nuevo en `users` (mismo que el backend viejo), separado de {@link UserController} porque
 * ninguno de sus otros endpoints vive bajo {@code /profile}.
 *
 * <p>Actor: ver nota de {@link UserController} — sesion primero, header {@code X-Actor-Id}
 * como respaldo temporal de la migracion. */
@RestController
@RequestMapping("/api/v1/profile")
public class LogrosController {

    private final GetLogrosUseCase getLogrosUseCase;

    public LogrosController(GetLogrosUseCase getLogrosUseCase) {
        this.getLogrosUseCase = getLogrosUseCase;
    }

    @RequiresPermission(Permission.USE_APP)
    @GetMapping("/logros")
    public LogrosResponse logros(@ActorAutenticado UserId actor) {
        return LogrosResponse.from(getLogrosUseCase.getLogros(new GetLogrosQuery(actor)));
    }
}
