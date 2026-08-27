package com.renaser.os.users.application.services;

import com.renaser.os.shared.domain.CodigoVerificacionInvalidoException;
import com.renaser.os.shared.domain.RateLimitExceededException;
import com.renaser.os.users.application.ports.in.autenticacion.ConfirmarCodigoVerificacionEmailUseCase;
import com.renaser.os.users.application.ports.in.autenticacion.EnviarCodigoVerificacionEmailUseCase;
import com.renaser.os.users.application.ports.out.autenticacion.CodigoVerificacionEmailPort;
import com.renaser.os.users.application.ports.out.autenticacion.EnviarEmailPort;
import com.renaser.os.users.application.ports.out.autenticacion.LimitarSolicitudesResetPort;
import com.renaser.os.users.application.ports.out.autenticacion.TokenVerificacionEmailPort;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Verificacion de propiedad de un email ANTES del alta de cuenta (2026-08-27,
 * docs/PLAN_INTEGRACION_FRONTEND.md — reemplaza el {@code signInWithOtp}/{@code verifyOtp} de
 * Supabase que el registro ya no puede usar desde que {@code AccountRequestService.submit}
 * genera su propio UUID). Disenado contra guias externas, no inventado (ver commit): OWASP
 * Multifactor Authentication Cheat Sheet ("apply strict attempt limits" — de ahi
 * {@link #MAX_INTENTOS}) y Forgot Password Cheat Sheet (token de un solo uso, invalidado tras
 * usarse). Spring Security tiene su propio "One-Time Token Login" (6.4+) pero exige un usuario
 * YA EXISTENTE y almacenamiento JDBC — no encaja: aca el email todavia no es nadie, y una tabla
 * nueva violaria D-40 (BD congelada). Mismo patron Redis que {@link ResetContrasenaService},
 * dos piezas separadas a proposito:
 *
 * <ul>
 *   <li>{@link CodigoVerificacionEmailPort}: el codigo de 6 digitos que una PERSONA tipea —
 *       corto, memorizable, con limite de intentos porque su espacio (1 millon) es chico.</li>
 *   <li>{@link TokenVerificacionEmailPort}: el token opaco que el CLIENTE guarda y reenvia con
 *       el resto del formulario de alta — alta entropia, no pensado para que nadie lo tipee.</li>
 * </ul>
 */
@Service
class VerificacionEmailService implements EnviarCodigoVerificacionEmailUseCase, ConfirmarCodigoVerificacionEmailUseCase {

    /** 10 minutos: Spring Security OTT usa 5 min por defecto: se da margen de usabilidad
     * (revisar el correo no es instantaneo) sin llegar a la vigencia de 30 min que usa el link
     * de activacion de cuenta (ese es para un correo que se puede mirar horas despues; este es
     * para un codigo que se tipea en el momento). */
    static final Duration VIGENCIA_CODIGO = Duration.ofMinutes(10);

    /** OWASP Multifactor Authentication Cheat Sheet: "apply strict attempt limits". Con 6
     * digitos (1 millon de combinaciones), 5 intentos deja una probabilidad de acierto por
     * fuerza bruta de 0.0005% antes de que el codigo se invalide solo. */
    static final int MAX_INTENTOS = 5;

    /** Tiempo para completar el resto del formulario de alta despues de verificar el correo. */
    static final Duration VIGENCIA_TOKEN_VERIFICACION = Duration.ofMinutes(30);

    /** Mismos umbrales que {@code ResetContrasenaService} (documentados ahi como asuncion, no
     * confirmados por producto — A-5): se repiten aca por consistencia, no por certeza nueva. */
    static final Duration VENTANA_RATE_LIMIT = Duration.ofHours(1);
    static final int LIMITE_POR_EMAIL = 5;
    static final int LIMITE_POR_IP = 20;

    private final CodigoVerificacionEmailPort codigoVerificacionEmailPort;
    private final TokenVerificacionEmailPort tokenVerificacionEmailPort;
    private final LimitarSolicitudesResetPort limitarSolicitudesResetPort;
    private final EnviarEmailPort enviarEmailPort;

    VerificacionEmailService(CodigoVerificacionEmailPort codigoVerificacionEmailPort,
                              TokenVerificacionEmailPort tokenVerificacionEmailPort,
                              LimitarSolicitudesResetPort limitarSolicitudesResetPort,
                              EnviarEmailPort enviarEmailPort) {
        this.codigoVerificacionEmailPort = codigoVerificacionEmailPort;
        this.tokenVerificacionEmailPort = tokenVerificacionEmailPort;
        this.limitarSolicitudesResetPort = limitarSolicitudesResetPort;
        this.enviarEmailPort = enviarEmailPort;
    }

    @Override
    public void enviar(EnviarCodigoVerificacionEmailCommand command) {
        rejectIfRateLimitExceeded(command.email(), command.requestIp());
        String codigo = codigoVerificacionEmailPort.generarCodigo(command.email(), VIGENCIA_CODIGO);
        enviarEmailPort.enviarCodigoVerificacionEmail(command.email(), codigo);
    }

    @Override
    public ResultadoVerificacion confirmar(ConfirmarCodigoVerificacionEmailCommand command) {
        boolean coincide = codigoVerificacionEmailPort.verificarCodigo(command.email(), command.codigo(),
                MAX_INTENTOS);
        if (!coincide) {
            throw new CodigoVerificacionInvalidoException();
        }
        String token = tokenVerificacionEmailPort.generar(command.email(), VIGENCIA_TOKEN_VERIFICACION);
        return new ResultadoVerificacion(token);
    }

    private void rejectIfRateLimitExceeded(String email, String requestIp) {
        if (!limitarSolicitudesResetPort.registrarIntento("email-verification:email:" + email, VENTANA_RATE_LIMIT,
                LIMITE_POR_EMAIL)) {
            throw new RateLimitExceededException("Limite de solicitudes de verificacion de correo excedido");
        }
        if (requestIp != null && !limitarSolicitudesResetPort.registrarIntento("email-verification:ip:" + requestIp,
                VENTANA_RATE_LIMIT, LIMITE_POR_IP)) {
            throw new RateLimitExceededException("Limite de solicitudes de verificacion de correo excedido");
        }
    }
}
