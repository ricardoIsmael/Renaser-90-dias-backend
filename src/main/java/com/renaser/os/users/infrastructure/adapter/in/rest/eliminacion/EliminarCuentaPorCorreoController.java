package com.renaser.os.users.infrastructure.adapter.in.rest.eliminacion;

import com.renaser.os.shared.web.DireccionIpDelCliente;
import com.renaser.os.shared.web.security.PublicEndpoint;
import com.renaser.os.users.application.ports.in.eliminacion.CerrarCuentaPorCorreoUseCase;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * La pagina web publica para eliminar la cuenta (D-243; Google Play pide un enlace web): se pide un
 * codigo al correo y con el se confirma. Sin sesion, por definicion: es para quien no tiene la app.
 */
@RestController
@RequestMapping("/api/v1/account-deletion")
public class EliminarCuentaPorCorreoController {

    private final CerrarCuentaPorCorreoUseCase cerrarPorCorreo;

    public EliminarCuentaPorCorreoController(CerrarCuentaPorCorreoUseCase cerrarPorCorreo) {
        this.cerrarPorCorreo = cerrarPorCorreo;
    }

    @PublicEndpoint("Pagina publica de Google Play para eliminar la cuenta sin la app. Responde 202 siempre, "
            + "para no revelar si el correo tiene cuenta.")
    @PostMapping("/request-code")
    public ResponseEntity<Void> solicitarCodigo(@RequestBody @Valid SolicitarCodigoEliminacionRequest request,
                                                HttpServletRequest servletRequest) {
        cerrarPorCorreo.solicitarCodigo(request.email(), DireccionIpDelCliente.de(servletRequest));
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    @PublicEndpoint("La credencial es el codigo enviado al correo de la cuenta.")
    @PostMapping("/confirm")
    public CuentaCerradaResponse confirmar(@RequestBody @Valid ConfirmarEliminacionRequest request,
                                           HttpServletRequest servletRequest) {
        return CuentaCerradaResponse.from(cerrarPorCorreo.confirmar(request.email(), request.codigo(),
                DireccionIpDelCliente.de(servletRequest)));
    }
}
