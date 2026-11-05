package com.renaser.os.calendar.infrastructure.adapter.in.rest.meet;

import com.renaser.os.calendar.application.ports.in.meet.CuentaDeGoogleUseCase;
import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import com.renaser.os.shared.web.security.Funcionalidad;
import com.renaser.os.shared.web.security.RequiresPermission;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * La cuenta de Google de Renaser, la que organiza las salas de Meet (spec 040, FR-010 y FR-015): ver su estado, empezar a conectarla y
 * desconectarla. Solo ADMIN y ALCHEMIST ({@code MANAGE_CALENDAR}; el servicio lo vuelve a comprobar). Funcionan aunque {@code eventos.meet}
 * esté apagada: hay que poder conectar la cuenta antes de prender la función.
 */
@RestController
@RequestMapping("/api/v1/admin/integraciones/google")
@Funcionalidad("eventos.gestion")
class CuentaDeGoogleController {

    private final CuentaDeGoogleUseCase cuentaUseCase;

    CuentaDeGoogleController(CuentaDeGoogleUseCase cuentaUseCase) {
        this.cuentaUseCase = cuentaUseCase;
    }

    @RequiresPermission(value = Permission.MANAGE_CALENDAR, scope = "solo ADMIN/ALCHEMIST (D-186): el servicio rechaza a MENTOR, MENTOR_LEAD y TRAINEE")
    @GetMapping
    public CuentaDeGoogleResponse estado(@ActorAutenticado UserId actor) {
        return CuentaDeGoogleResponse.from(cuentaUseCase.estado(actor));
    }

    /** Devuelve la dirección de Google adonde mandar a la persona; Google la devuelve al regreso ({@link RegresoDeGoogleController}). */
    @RequiresPermission(value = Permission.MANAGE_CALENDAR, scope = "solo ADMIN/ALCHEMIST (D-186): el servicio rechaza a MENTOR, MENTOR_LEAD y TRAINEE")
    @PostMapping("/connect")
    public ConexionResponse conectar(@ActorAutenticado UserId actor) {
        return new ConexionResponse(cuentaUseCase.iniciarConexion(actor));
    }

    @RequiresPermission(value = Permission.MANAGE_CALENDAR, scope = "solo ADMIN/ALCHEMIST (D-186): el servicio rechaza a MENTOR, MENTOR_LEAD y TRAINEE")
    @DeleteMapping
    public ResponseEntity<Void> desconectar(@ActorAutenticado UserId actor) {
        cuentaUseCase.desconectar(actor);
        return ResponseEntity.noContent().build();
    }

    record ConexionResponse(String url) {
    }
}
