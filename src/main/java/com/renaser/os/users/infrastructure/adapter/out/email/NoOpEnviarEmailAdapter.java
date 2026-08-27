package com.renaser.os.users.infrastructure.adapter.out.email;

import com.renaser.os.users.application.ports.out.autenticacion.EnviarEmailPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Placeholder sin proveedor de email real (Resend, SES, etc.) — no hay credenciales todavia y
 * CLAUDE.MD prohibe integrar un proveedor real sin que se pida explicitamente. Mismo patron que
 * {@code NoOpPushAdapter}/{@code NoOpSupabaseAdminAuthAdapter}: el resto del flujo de reseteo
 * queda completo y probado detras de {@link EnviarEmailPort}, listo para enchufar un adaptador
 * real (Resend/SES) sin tocar el caso de uso.
 */
@Component
public class NoOpEnviarEmailAdapter implements EnviarEmailPort {

    private static final Logger log = LoggerFactory.getLogger(NoOpEnviarEmailAdapter.class);

    /**
     * URL base de la pantalla de "nueva contrasena" del frontend — todavia no definida por
     * producto (docs/MODULO_AUTH.md fase 7). Placeholder de configuracion a proposito: NO se
     * inventa un dominio real aca. El link completo seria {@code <base>?token=<token>}.
     */
    private final String resetPasswordUrlBase;
    private final String activateAccountUrlBase;

    public NoOpEnviarEmailAdapter(@Value("${renaser.web.reset-password-url}") String resetPasswordUrlBase,
                                   @Value("${renaser.web.activate-account-url}") String activateAccountUrlBase) {
        this.resetPasswordUrlBase = resetPasswordUrlBase;
        this.activateAccountUrlBase = activateAccountUrlBase;
    }

    @Override
    public void enviarResetContrasena(String destinatarioEmail, String token) {
        // Ni el email (PII) ni el token (credencial) se loguean nunca (CLAUDE.MD §5.4.9, regla
        // dura del alcance de esta fase). Se arma el link igual, para demostrar que la
        // configuracion esta bien conectada, pero solo se deja constancia de su longitud.
        String link = resetPasswordUrlBase + "?token=" + token;
        log.info("[users.NoOpEnviarEmailAdapter] email de reset de contrasena simulado "
                + "(adaptador placeholder, sin proveedor real todavia; link de {} caracteres armado)",
                link.length());
    }

    @Override
    public void enviarInvitacionStaff(String destinatarioEmail, String temporaryPassword) {
        // Ni el email (PII) ni la contrasena temporal (credencial) se loguean nunca
        // (CLAUDE.MD §5.4.9) — mismo criterio que enviarResetContrasena.
        log.info("[users.NoOpEnviarEmailAdapter] email de invitacion de staff simulado "
                + "(adaptador placeholder, sin proveedor real todavia; contrasena temporal de {} caracteres generada)",
                temporaryPassword.length());
    }

    @Override
    public void enviarActivacionCuenta(String destinatarioEmail, String token) {
        // Mismo criterio de privacidad que enviarResetContrasena: ni email ni token en el log.
        String link = activateAccountUrlBase + "?token=" + token;
        log.info("[users.NoOpEnviarEmailAdapter] email de activacion de cuenta simulado "
                + "(adaptador placeholder, sin proveedor real todavia; link de {} caracteres armado)",
                link.length());
    }

    @Override
    public void enviarCodigoVerificacionEmail(String destinatarioEmail, String codigo) {
        // Ni el email (PII) ni el codigo (credencial de un solo uso) se loguean nunca
        // (CLAUDE.MD §5.4.9) — mismo criterio que el resto de este adaptador.
        log.info("[users.NoOpEnviarEmailAdapter] email de codigo de verificacion simulado "
                + "(adaptador placeholder, sin proveedor real todavia; codigo de {} digitos generado)",
                codigo.length());
    }
}
