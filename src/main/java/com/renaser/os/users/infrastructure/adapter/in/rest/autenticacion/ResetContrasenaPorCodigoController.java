package com.renaser.os.users.infrastructure.adapter.in.rest.autenticacion;

import com.renaser.os.shared.web.security.PublicEndpoint;
import com.renaser.os.users.application.ports.in.autenticacion.SolicitarCodigoResetContrasenaUseCase;
import com.renaser.os.users.application.ports.in.autenticacion.SolicitarCodigoResetContrasenaUseCase.SolicitarCodigoResetContrasenaCommand;
import com.renaser.os.users.application.ports.in.autenticacion.VerificarCodigoResetContrasenaUseCase;
import com.renaser.os.users.application.ports.in.autenticacion.VerificarCodigoResetContrasenaUseCase.VerificarCodigoResetContrasenaCommand;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Recuperacion de contrasena POR CODIGO, dentro de la app (2026-09-04, D-102, docs/MODULO_AUTH.md
 * §7.6). Pedido textual del dueno del producto: "que salga para enviar correo, lo mismo con OTP.
 * Verifico el OTP y pongo la nueva contrasena". Los dos pasos de aca reemplazan al link por
 * correo del flujo de §7.5 — que sigue existiendo en {@link AutenticacionController} para un
 * frontend web — y el tercer paso es el que ya estaba: {@code POST /auth/password/reset-confirm}
 * con el {@code resetToken} que devuelve {@code /verify-code}.
 *
 * <p>Controller aparte, mismo precedente que {@link VerificacionEmailController} para el OTP del
 * alta: {@code AutenticacionController} ya tiene ocho endpoints y ocho colaboradores.
 */
@RestController
@RequestMapping("/api/v1/auth/password")
public class ResetContrasenaPorCodigoController {

    private final SolicitarCodigoResetContrasenaUseCase solicitarCodigoUseCase;
    private final VerificarCodigoResetContrasenaUseCase verificarCodigoUseCase;

    public ResetContrasenaPorCodigoController(SolicitarCodigoResetContrasenaUseCase solicitarCodigoUseCase,
                                               VerificarCodigoResetContrasenaUseCase verificarCodigoUseCase) {
        this.solicitarCodigoUseCase = solicitarCodigoUseCase;
        this.verificarCodigoUseCase = verificarCodigoUseCase;
    }

    /**
     * Responde 202 SIEMPRE, exista o no una cuenta con ese email: el caso de uso ya garantiza que
     * el comportamiento observable es identico (no-enumeracion, igual que {@code /reset-request}).
     */
    @PublicEndpoint("Se pide justamente cuando no se puede iniciar sesion. Responde 202 siempre, para no revelar si el correo existe.")
    @PostMapping("/forgot")
    public ResponseEntity<Void> solicitarCodigo(@RequestBody @Valid SolicitarResetContrasenaRequest request,
                                                HttpServletRequest servletRequest) {
        solicitarCodigoUseCase.solicitarCodigo(
                new SolicitarCodigoResetContrasenaCommand(request.email(), servletRequest.getRemoteAddr()));
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    /** 200 con el {@code resetToken}; 400 ({@code CodigoVerificacionInvalidoException}) si el codigo no sirve. */
    @PublicEndpoint("La credencial es el codigo enviado al correo; quien lo verifica todavia no puede iniciar sesion.")
    @PostMapping("/verify-code")
    public CodigoResetVerificadoResponse verificarCodigo(@RequestBody @Valid VerificarCodigoResetContrasenaRequest request) {
        var resultado = verificarCodigoUseCase.verificarCodigo(
                new VerificarCodigoResetContrasenaCommand(request.email(), request.codigo()));
        return CodigoResetVerificadoResponse.from(resultado);
    }
}
