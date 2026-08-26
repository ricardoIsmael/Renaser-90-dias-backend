package com.renaser.os.users.application.services;

import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.RateLimitExceededException;
import com.renaser.os.shared.domain.TokenResetInvalidoException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.application.ports.in.autenticacion.CerrarTodasLasSesionesUseCase;
import com.renaser.os.users.application.ports.in.autenticacion.ConfirmarResetContrasenaUseCase;
import com.renaser.os.users.application.ports.in.autenticacion.SolicitarResetContrasenaUseCase;
import com.renaser.os.users.application.ports.out.autenticacion.EnviarEmailPort;
import com.renaser.os.users.application.ports.out.autenticacion.LimitarSolicitudesResetPort;
import com.renaser.os.users.application.ports.out.autenticacion.LoadCredencialPort;
import com.renaser.os.users.application.ports.out.autenticacion.LoadCredencialPort.CredencialParaLogin;
import com.renaser.os.users.application.ports.out.autenticacion.SaveCredencialPort;
import com.renaser.os.users.application.ports.out.autenticacion.TokenResetContrasenaPort;
import com.renaser.os.users.domain.model.user.Credencial;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

/**
 * Reseteo de contrasena por link de un solo uso (docs/MODULO_AUTH.md fase 7, §2.2). No hay
 * tabla de tokens (BD congelada, D-40): el token vive en Redis con TTL de 30 min via
 * {@link TokenResetContrasenaPort}, que ya garantiza la atomicidad del "un solo uso".
 */
@Service
public class ResetContrasenaService implements SolicitarResetContrasenaUseCase, ConfirmarResetContrasenaUseCase {

    /** 30 minutos, fijado en docs/MODULO_AUTH.md §2.2 ("es un token de un solo uso que vive 30 minutos"). */
    static final Duration VIGENCIA_TOKEN = Duration.ofMinutes(30);

    /**
     * Umbrales de la ventana de una hora. No estan especificados en CLAUDE.MD/MODULO_AUTH.md
     * (que solo piden "rate limit por email y por IP", sin numeros) — se asumen estos valores
     * por analogia con el unico precedente del repo (60/hora por IP en
     * {@code AccountRequestService}), quedan documentados como decision a confirmar.
     */
    static final Duration VENTANA_RATE_LIMIT = Duration.ofHours(1);
    static final int LIMITE_POR_EMAIL = 5;
    static final int LIMITE_POR_IP = 20;

    private final LoadCredencialPort loadCredencialPort;
    private final SaveCredencialPort saveCredencialPort;
    private final TokenResetContrasenaPort tokenResetContrasenaPort;
    private final LimitarSolicitudesResetPort limitarSolicitudesResetPort;
    private final EnviarEmailPort enviarEmailPort;
    private final CerrarTodasLasSesionesUseCase cerrarTodasLasSesionesUseCase;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public ResetContrasenaService(LoadCredencialPort loadCredencialPort, SaveCredencialPort saveCredencialPort,
                                   TokenResetContrasenaPort tokenResetContrasenaPort,
                                   LimitarSolicitudesResetPort limitarSolicitudesResetPort,
                                   EnviarEmailPort enviarEmailPort,
                                   CerrarTodasLasSesionesUseCase cerrarTodasLasSesionesUseCase,
                                   PasswordEncoder passwordEncoder, Clock clock) {
        this.loadCredencialPort = loadCredencialPort;
        this.saveCredencialPort = saveCredencialPort;
        this.tokenResetContrasenaPort = tokenResetContrasenaPort;
        this.limitarSolicitudesResetPort = limitarSolicitudesResetPort;
        this.enviarEmailPort = enviarEmailPort;
        this.cerrarTodasLasSesionesUseCase = cerrarTodasLasSesionesUseCase;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Override
    public void solicitar(SolicitarResetContrasenaCommand command) {
        rejectIfRateLimitExceeded(command.email(), command.requestIp());

        // Sin `else`: si no hay cuenta, o la cuenta no tiene contrasena (solo entra por
        // proveedor social), el metodo simplemente no hace nada mas — misma no-enumeracion que
        // el login (CLAUDE.MD §5.3.3).
        loadCredencialPort.porEmail(command.email())
                .filter(CredencialParaLogin::permiteLoginPorContrasena)
                .ifPresent(credencial -> emitirTokenYEnviarCorreo(credencial.usuarioId(), command.email()));
    }

    @Override
    @Transactional
    public void confirmar(ConfirmarResetContrasenaCommand command) {
        UserId usuarioId = tokenResetContrasenaPort.consumir(command.token())
                .orElseThrow(TokenResetInvalidoException::new);

        Credencial nueva = new Credencial(passwordEncoder.encode(command.contrasenaNueva()), clock.now());
        saveCredencialPort.guardar(usuarioId, nueva);

        // Razon de negocio completa de este paso (CLAUDE.MD, docs/MODULO_AUTH.md §4.1): cambiar
        // la contrasena por sospecha de robo debe invalidar cualquier sesion que el atacante ya
        // tenga abierta, no solo impedir logins futuros.
        cerrarTodasLasSesionesUseCase.cerrarTodas(usuarioId);
    }

    private void emitirTokenYEnviarCorreo(UserId usuarioId, String email) {
        String token = tokenResetContrasenaPort.generar(usuarioId, VIGENCIA_TOKEN);
        enviarEmailPort.enviarResetContrasena(email, token);
    }

    private void rejectIfRateLimitExceeded(String email, String requestIp) {
        if (!limitarSolicitudesResetPort.registrarIntento("email:" + email, VENTANA_RATE_LIMIT, LIMITE_POR_EMAIL)) {
            throw new RateLimitExceededException("Limite de solicitudes de reseteo de contrasena excedido");
        }
        if (requestIp != null
                && !limitarSolicitudesResetPort.registrarIntento("ip:" + requestIp, VENTANA_RATE_LIMIT, LIMITE_POR_IP)) {
            throw new RateLimitExceededException("Limite de solicitudes de reseteo de contrasena excedido");
        }
    }
}
