package com.renaser.os.users.infrastructure.adapter.in.rest.eliminacion;

import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import com.renaser.os.shared.web.security.RequiresPermission;
import com.renaser.os.users.application.ports.in.eliminacion.CerrarMiCuentaUseCase;
import com.renaser.os.users.application.ports.in.eliminacion.CerrarMiCuentaUseCase.CerrarMiCuentaCommand;
import com.renaser.os.users.infrastructure.adapter.in.web.security.SesionWebAdapter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Yo → «Eliminar mi cuenta» (D-243). Siempre sobre la cuenta de la sesion: no hay id en la ruta ni en
 * el cuerpo, asi que nadie puede eliminar la cuenta de otro por aca.
 *
 * <p>Reemplaza a {@code GET/POST/DELETE /me/account-deletion} de la baja vieja (gap #5, 14 dias con
 * acceso y cancelable por la persona), que ninguna version de la app llego a usar.
 */
@RestController
@RequestMapping("/api/v1/users/me/account-deletion")
public class EliminarMiCuentaController {

    private final CerrarMiCuentaUseCase cerrarMiCuenta;
    private final SesionWebAdapter sesionWeb;

    public EliminarMiCuentaController(CerrarMiCuentaUseCase cerrarMiCuenta, SesionWebAdapter sesionWeb) {
        this.cerrarMiCuenta = cerrarMiCuenta;
        this.sesionWeb = sesionWeb;
    }

    @RequiresPermission(value = Permission.USE_APP, scope = "self")
    @GetMapping
    public ComoConfirmarResponse comoConfirmar(@ActorAutenticado UserId actor) {
        return ComoConfirmarResponse.from(cerrarMiCuenta.comoConfirmar(actor));
    }

    @RequiresPermission(value = Permission.USE_APP, scope = "self")
    @PostMapping("/code")
    public ResponseEntity<Void> enviarCodigo(@ActorAutenticado UserId actor) {
        cerrarMiCuenta.enviarCodigo(actor);
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    /** Despues del cierre esta misma sesion tambien se invalida: la respuesta es lo ultimo que ve. */
    @RequiresPermission(value = Permission.USE_APP, scope = "self")
    @PostMapping
    public CuentaCerradaResponse cerrar(@ActorAutenticado UserId actor, @RequestBody @Valid CerrarMiCuentaRequest request,
                                        HttpServletRequest servletRequest) {
        var estado = cerrarMiCuenta.cerrar(new CerrarMiCuentaCommand(actor, request.contrasena(), request.codigo()));
        sesionWeb.cerrar(servletRequest);
        return CuentaCerradaResponse.from(estado);
    }
}
