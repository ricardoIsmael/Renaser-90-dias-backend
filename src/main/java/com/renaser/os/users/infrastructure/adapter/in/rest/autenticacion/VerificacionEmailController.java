package com.renaser.os.users.infrastructure.adapter.in.rest.autenticacion;

import com.renaser.os.shared.web.security.PublicEndpoint;
import com.renaser.os.users.application.ports.in.autenticacion.ConfirmarCodigoVerificacionEmailUseCase;
import com.renaser.os.users.application.ports.in.autenticacion.ConfirmarCodigoVerificacionEmailUseCase.ConfirmarCodigoVerificacionEmailCommand;
import com.renaser.os.users.application.ports.in.autenticacion.EnviarCodigoVerificacionEmailUseCase;
import com.renaser.os.users.application.ports.in.autenticacion.EnviarCodigoVerificacionEmailUseCase.EnviarCodigoVerificacionEmailCommand;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Prueba que el solicitante controla su correo ANTES del alta (gap dejado por Supabase Auth,
 * 2026-08-27, ver javadoc de {@code VerificacionEmailService}). Publico, sin sesion: quien
 * llama todavia no tiene cuenta.
 */
@RestController
@RequestMapping("/api/v1/auth/email-verification")
public class VerificacionEmailController {

    private final EnviarCodigoVerificacionEmailUseCase enviarUseCase;
    private final ConfirmarCodigoVerificacionEmailUseCase confirmarUseCase;

    public VerificacionEmailController(EnviarCodigoVerificacionEmailUseCase enviarUseCase,
                                        ConfirmarCodigoVerificacionEmailUseCase confirmarUseCase) {
        this.enviarUseCase = enviarUseCase;
        this.confirmarUseCase = confirmarUseCase;
    }

    @PublicEndpoint("El correo se verifica antes de tener cuenta. Protegido por rate limit por IP, no por autorizacion.")
    @PostMapping("/send")
    public ResponseEntity<Void> enviar(@RequestBody @Valid EnviarCodigoVerificacionEmailRequest request,
                                        HttpServletRequest servletRequest) {
        enviarUseCase.enviar(new EnviarCodigoVerificacionEmailCommand(request.email(),
                servletRequest.getRemoteAddr()));
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    @PublicEndpoint("La credencial es el codigo enviado al correo; quien lo confirma todavia no tiene cuenta.")
    @PostMapping("/confirm")
    public VerificacionEmailResponse confirmar(@RequestBody @Valid ConfirmarCodigoVerificacionEmailRequest request) {
        var resultado = confirmarUseCase.confirmar(
                new ConfirmarCodigoVerificacionEmailCommand(request.email(), request.codigo()));
        return VerificacionEmailResponse.from(resultado);
    }
}
